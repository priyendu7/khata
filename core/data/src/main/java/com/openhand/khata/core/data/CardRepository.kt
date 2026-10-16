package com.openhand.khata.core.data

import androidx.room.withTransaction
import com.openhand.khata.core.database.CardPaymentRow
import com.openhand.khata.core.database.CardSpendRow
import com.openhand.khata.core.database.CardStatementEntity
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.database.entity.AccountEntity
import com.openhand.khata.core.database.entity.TransactionEntity
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.CardCycle
import com.openhand.khata.core.model.CardCycles
import com.openhand.khata.core.model.CardPayment
import com.openhand.khata.core.model.CardStatement
import com.openhand.khata.core.model.CategorySpend
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.PaymentStatus
import com.openhand.khata.core.model.SmsStatement
import com.openhand.khata.core.model.StatementPayments
import com.openhand.khata.core.model.StatementSummary
import com.openhand.khata.core.model.TransactionSource
import com.openhand.khata.core.model.TransferKind
import com.openhand.khata.core.model.TransferSide
import com.openhand.khata.core.model.UnlinkedPayment
import dagger.Lazy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Credit cards' statements, cycles and whether they're paid (#154), for the Cards screens (#155)
 * and due date reminders (#156).
 *
 * A statement is paid by transfers into its card from around its statement date until the next
 * statement's, as [StatementPayments] decides. A bill paid from a bank account counts through its card side, so a CRED
 * payment and the card's "payment received" that #113 linked count once; one with no card side
 * saved counts only when there's a single credit card it can be for.
 */
@Singleton
class CardRepository @Inject constructor(
    private val db: Lazy<KhataDatabase>,
    private val transactions: TransactionRepository
) {
    /**
     * Saves a statement read from SMS on the credit card with its bank and last 4 digits, making
     * the card if there's none. The receiver and an inbox scan can both see it: it's saved once.
     */
    suspend fun save(sms: SmsStatement): StatementSaveResult = db.io { database ->
        database.withTransaction {
            val accounts = database.accountDao()
            val dao = database.cardStatementDao()
            val card = AccountMatch.find(accounts, sms.bank, sms.cardLast4) {
                it.type == AccountType.CREDIT_CARD
            }
            val rawSms = SmsLinks.strip(sms.rawSms)
            val dueDay = sms.dueDate.toEpochDay()
            dao.findSame(card?.id, dueDay, sms.totalPaise, rawSms)
                ?.let { return@withTransaction StatementSaveResult.Duplicate(it) }
            val cardId = card?.id ?: accounts.insert(
                AccountEntity(
                    name = "${sms.bank} ${sms.cardLast4}",
                    type = AccountType.CREDIT_CARD,
                    bank = sms.bank,
                    last4 = sms.cardLast4
                )
            )
            val id = dao.insert(
                CardStatementEntity(
                    accountId = cardId,
                    statementDate = sms.statementDate.toEpochDay(),
                    dueDate = dueDay,
                    totalPaise = sms.totalPaise,
                    minDuePaise = sms.minDuePaise,
                    rawSms = rawSms,
                    dateFromSms = sms.dateFromSms
                )
            )
            StatementSaveResult.Saved(id, cardId)
        }
    }

    /**
     * Moves a statement to another credit card, for when two cards of one bank were told apart
     * wrongly (an SMS without the card's digits).
     */
    suspend fun moveStatement(statementId: Long, toCardId: Long) {
        db.io { database ->
            val card = requireNotNull(database.accountDao().getById(toCardId)) { "No such card" }
            require(card.type == AccountType.CREDIT_CARD) { "Statements go on credit cards" }
            database.cardStatementDao().move(statementId, toCardId)
        }
    }

    /**
     * Every credit card with its statements, newest first, and this cycle's spending, on
     * [today] in [zone]. Spending is expenses minus refunds, as everywhere else.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCards(
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Flow<List<CardSummary>> = combine(
        db.observe { it.accountDao().observeAll() },
        db.observe { it.cardStatementDao().observeAll() },
        db.observe { it.cardStatementDao().observePayments() }
    ) { accounts, statements, payments ->
        val cards = accounts.filter { it.type == AccountType.CREDIT_CARD }
        val byCard = statements.groupBy { it.accountId }
        cards.map { card ->
            val history = summaries(
                card,
                byCard[card.id].orEmpty(),
                paymentsTo(card, cards.size, payments),
                today,
                zone
            )
            CardPlan(
                card.toModel(),
                history,
                CardCycles.currentCycle(
                    history.firstOrNull()?.statement?.statementDate,
                    card.billingDay,
                    today
                )
            )
        }
    }.flatMapLatest { plans ->
        val from = plans.flatMap { listOfNotNull(it.current, it.latestCycle) }
            .minOfOrNull { it.first }
        if (from == null) {
            flowOf(plans.map { it.summary(emptyList(), zone) })
        } else {
            db.observe { it.cardStatementDao().observeSpends(startOf(from, zone)) }
                .map { spends -> plans.map { it.summary(spends, zone) } }
        }
    }

    /** One credit card, as in [observeCards]; null once it's gone or isn't a credit card. */
    fun observeCard(
        cardId: Long,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Flow<CardSummary?> =
        observeCards(today, zone).map { cards -> cards.firstOrNull { it.card.id == cardId } }

    /**
     * The soonest due credit card bill that isn't paid in full, overdue ones first; null when
     * every card's latest statement is paid.
     */
    fun observeNextDue(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<CardDue?> =
        observeCards(today, zone).map { cards ->
            cards.mapNotNull { card ->
                card.latest?.takeUnless { it.status.isPaid }?.let { CardDue(card.card, it) }
            }.minByOrNull { it.statement.statement.dueDate }
        }

    /**
     * Every debit card with its bank account (#155) and its own spending in [from, until), as
     * epoch milliseconds; the account's spending includes it too, but nothing is counted twice.
     */
    fun observeDebitCards(from: Long, until: Long): Flow<List<DebitCardSummary>> = combine(
        db.observe { it.accountDao().observeAll() },
        db.observe { it.accountDao().observeDebitCardSpending(from, until) }
    ) { accounts, spends ->
        val byId = accounts.associateBy { it.id }
        val spent = spends.associate { it.accountId to it.spentPaise }
        accounts.filter { it.type == AccountType.DEBIT_CARD }.map { card ->
            DebitCardSummary(
                card = card.toModel(),
                linkedAccount = card.linkedAccountId?.let(byId::get)?.toModel(),
                spentPaise = spent[card.id] ?: 0
            )
        }
    }

    /** Card bills paid from other accounts whose card isn't known, newest first (#166). */
    fun observeUnlinkedPayments(): Flow<List<UnlinkedPayment>> =
        db.observe { it.cardStatementDao().observeUnlinkedPayments() }.map { rows ->
            rows.map {
                UnlinkedPayment(
                    it.transactionId,
                    it.amountPaise,
                    it.timestamp,
                    it.payeeName,
                    it.accountName
                )
            }
        }

    /**
     * Links bill payment [transactionId], made from another account, to credit card [cardId]
     * (#166): its card side is saved as the payment into the card, paired with it as #113 pairs
     * the two sides of a move, so it counts toward the card's statement.
     */
    suspend fun linkPayment(transactionId: Long, cardId: Long) {
        db.io { database ->
            database.withTransaction {
                val dao = database.transactionDao()
                val paid = requireNotNull(dao.getById(transactionId)) { "No such payment" }
                require(paid.direction == Direction.TRANSFER && paid.transferPairId == null) {
                    "Only a transfer on its own can be linked"
                }
                val card = requireNotNull(database.accountDao().getById(cardId)) { "No card" }
                require(card.type == AccountType.CREDIT_CARD) { "Payments go to credit cards" }
                val cardSide = dao.insert(
                    TransactionEntity(
                        amountPaise = paid.amountPaise,
                        direction = Direction.TRANSFER,
                        timestamp = paid.timestamp,
                        accountId = cardId,
                        payeeId = paid.payeeId,
                        categoryId = paid.categoryId,
                        note = null,
                        referenceNo = null,
                        source = TransactionSource.MANUAL,
                        rawSms = null,
                        transferSide = TransferSide.IN,
                        transferKind = TransferKind.CARD_PAYMENT
                    )
                )
                database.transferDao().linkTransfers(transactionId, cardSide)
            }
        }
    }

    /**
     * Undoes [linkPayment] for its card side [cardSideId]: the card side goes, and the payment
     * it was linked to is "card not known" again.
     */
    suspend fun unlinkPayment(cardSideId: Long) {
        db.io { database ->
            val cardSide = database.transactionDao().getById(cardSideId)
            // Anything else is a real transaction, never deleted from here.
            if (cardSide?.isLinkedPayment() == true) {
                database.transferDao().deleteUnlinking(cardSideId)
            }
        }
    }

    /** [card]'s spending by category in [cycle], with the same rules as Insights (#108). */
    fun observeCycleSpending(
        card: Account,
        cycle: CardCycle,
        zone: ZoneId = ZoneId.systemDefault()
    ): Flow<List<CategorySpend>> = transactions.observeCategorySpendingForAccount(
        startOf(cycle.first, zone),
        startOf(cycle.last.plusDays(1), zone),
        card
    )

    /** [card]'s statements, newest first, each with its cycle, payments and status. */
    private fun summaries(
        card: AccountEntity,
        statements: List<CardStatementEntity>,
        payments: List<CardPayment>,
        today: LocalDate,
        zone: ZoneId
    ): List<StatementSummary> {
        val oldestFirst = statements.map { it.toModel() }
            .sortedWith(compareBy({ it.statementDate }, { it.id }))
        val byStatement = StatementPayments.allocate(oldestFirst, payments) {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
        }
        return oldestFirst.mapIndexed { index, statement ->
            val previous = oldestFirst.getOrNull(index - 1)?.statementDate
            val paid = byStatement[statement.id].orEmpty()
            StatementSummary(
                statement = statement,
                cycle = CardCycles.statementCycle(
                    statement.statementDate,
                    previous,
                    card.billingDay
                ),
                payments = paid,
                status = PaymentStatus.of(
                    totalPaise = statement.totalPaise,
                    minDuePaise = statement.minDuePaise,
                    paidPaise = paid.sumOf { it.amountPaise },
                    dueDate = statement.dueDate,
                    today = today
                )
            )
        }.asReversed()
    }

    /**
     * Payments into [card], and, when it's the only one of [cardCount] credit cards, bills paid
     * from other accounts whose card side isn't saved.
     */
    private fun paymentsTo(
        card: AccountEntity,
        cardCount: Int,
        payments: List<CardPaymentRow>
    ): List<CardPayment> = payments.filter {
        if (it.accountType == AccountType.CREDIT_CARD) it.accountId == card.id else cardCount == 1
    }.map {
        CardPayment(
            transactionId = it.transactionId,
            amountPaise = it.amountPaise,
            timestamp = it.timestamp,
            linked = it.source == TransactionSource.MANUAL && it.pairId != null
        )
    }

    /** A card worked out up to its spending, which comes from a query of its own. */
    private class CardPlan(
        val card: Account,
        val statements: List<StatementSummary>,
        val current: CardCycle
    ) {
        val latestCycle: CardCycle? get() = statements.firstOrNull()?.cycle

        fun summary(spends: List<CardSpendRow>, zone: ZoneId): CardSummary {
            val mine = spends.filter { it.accountId == card.id }
            return CardSummary(
                card = card,
                statements = statements,
                latestSpentPaise = latestCycle?.let { spent(mine, it, zone) },
                currentCycle = current,
                currentSpentPaise = spent(mine, current, zone)
            )
        }

        private fun spent(spends: List<CardSpendRow>, cycle: CardCycle, zone: ZoneId): Long {
            val range = startOf(cycle.first, zone) until startOf(cycle.last.plusDays(1), zone)
            return spends.filter { it.timestamp in range }.sumOf {
                if (it.direction == Direction.REFUND) -it.amountPaise else it.amountPaise
            }
        }
    }

    private companion object {
        /** A card side [linkPayment] saved: made by hand, and paired with a payment. */
        fun TransactionEntity.isLinkedPayment() = source == TransactionSource.MANUAL &&
            transferPairId != null &&
            transferSide == TransferSide.IN &&
            transferKind == TransferKind.CARD_PAYMENT

        fun startOf(day: LocalDate, zone: ZoneId): Long =
            day.atStartOfDay(zone).toInstant().toEpochMilli()

        fun CardStatementEntity.toModel() = CardStatement(
            id = id,
            accountId = accountId,
            statementDate = LocalDate.ofEpochDay(statementDate),
            dueDate = LocalDate.ofEpochDay(dueDate),
            totalPaise = totalPaise,
            minDuePaise = minDuePaise,
            rawSms = rawSms,
            dateFromSms = dateFromSms
        )
    }
}

/**
 * A credit card for the Cards screens: its statements, newest first, what Khata recorded in the
 * latest one's cycle, and the cycle it's in now.
 */
data class CardSummary(
    val card: Account,
    val statements: List<StatementSummary>,
    /** Spending Khata recorded in the latest statement's cycle; null with no statement. */
    val latestSpentPaise: Long?,
    /** The cycle no statement has billed yet. */
    val currentCycle: CardCycle,
    val currentSpentPaise: Long
) {
    /** The current statement. */
    val latest: StatementSummary? get() = statements.firstOrNull()
}

/** A debit card for the Cards tab (#155): its bank account, if linked, and what it spent. */
data class DebitCardSummary(val card: Account, val linkedAccount: Account?, val spentPaise: Long)

/** A credit card bill not paid in full yet, for the Home card and reminders. */
data class CardDue(val card: Account, val statement: StatementSummary)

sealed interface StatementSaveResult {
    data class Saved(val statementId: Long, val cardId: Long) : StatementSaveResult

    /** The same statement is saved already, as [existingId]. */
    data class Duplicate(val existingId: Long) : StatementSaveResult
}
