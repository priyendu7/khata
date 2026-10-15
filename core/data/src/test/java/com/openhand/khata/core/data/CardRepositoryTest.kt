package com.openhand.khata.core.data

import com.openhand.khata.core.database.entity.AccountEntity
import com.openhand.khata.core.database.entity.TransactionEntity
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.CardCycle
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.PaymentStatus
import com.openhand.khata.core.model.SmsStatement
import com.openhand.khata.core.model.SmsTransaction
import com.openhand.khata.core.model.TransactionSource
import com.openhand.khata.core.model.TransferKind
import com.openhand.khata.core.model.TransferSide
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CardRepositoryTest : RepositoryTest() {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val cards by lazy { CardRepository(lazyDb, TransactionRepository(lazyDb)) }
    private val importer by lazy { SmsImporter(lazyDb) }

    private fun day(text: String) = LocalDate.parse(text)

    private fun at(date: String, hour: Int = 12) =
        day(date).atTime(LocalTime.of(hour, 0)).atZone(zone).toInstant().toEpochMilli()

    private fun statement(
        statementDate: String = "2026-10-01",
        due: String = "2026-10-23",
        total: Long = 35_299_72,
        minDue: Long? = 3_450_00,
        bank: String = "ICICI",
        last4: String = "1234",
        body: String = "ICICI Bank Credit Card XX$last4 Statement: total $total due by $due"
    ) = SmsStatement(bank, last4, total, minDue, day(due), day(statementDate), body)

    private suspend fun saved(sms: SmsStatement) = cards.save(sms) as StatementSaveResult.Saved

    private suspend fun addCard(
        bank: String? = "ICICI",
        last4: String? = "1234",
        billingDay: Int? = null
    ) = db.accountDao().insert(
        AccountEntity(
            name = "$bank $last4",
            type = AccountType.CREDIT_CARD,
            bank = bank,
            last4 = last4,
            billingDay = billingDay
        )
    )

    /** A payment into [card], as a card's "payment received" SMS is saved. */
    private suspend fun pay(card: Long, amount: Long, at: Long) = db.transactionDao().insert(
        TransactionEntity(
            amountPaise = amount,
            direction = Direction.TRANSFER,
            timestamp = at,
            accountId = card,
            payeeId = null,
            categoryId = uncategorizedId(),
            note = null,
            referenceNo = null,
            source = TransactionSource.SMS,
            rawSms = null,
            transferSide = TransferSide.IN,
            transferKind = TransferKind.CARD_PAYMENT
        )
    )

    private suspend fun spend(
        card: Long,
        amount: Long,
        at: Long,
        direction: Direction = Direction.DEBIT
    ) = db.transactionDao().insert(
        TransactionEntity(
            amountPaise = amount,
            direction = direction,
            timestamp = at,
            accountId = card,
            payeeId = null,
            categoryId = uncategorizedId(),
            note = null,
            referenceNo = null,
            source = TransactionSource.MANUAL,
            rawSms = null
        )
    )

    private suspend fun card(id: Long, today: String = "2026-10-15") =
        cards.observeCard(id, day(today), zone).first()!!

    private suspend fun latest(id: Long, today: String = "2026-10-15") = card(id, today).latest!!

    @Test
    fun cyclesComeFromTwoStatements() = runTest {
        val card = saved(statement("2026-09-01", due = "2026-09-23")).cardId
        saved(statement("2026-10-01", due = "2026-10-23", total = 1_000_00))

        val summary = card(card)
        assertEquals(
            listOf(day("2026-10-01"), day("2026-09-01")),
            summary.statements.map { it.statement.statementDate }
        )
        assertEquals(CardCycle(day("2026-09-02"), day("2026-10-01")), summary.latest!!.cycle)
        // The first statement's cycle covers the month before.
        assertEquals(
            CardCycle(day("2026-08-02"), day("2026-09-01")),
            summary.statements[1].cycle
        )
        assertEquals(CardCycle(day("2026-10-02"), day("2026-11-01")), summary.currentCycle)
    }

    @Test
    fun beforeTheFirstStatementTheBillingDaySetsTheCycle() = runTest {
        val card = addCard(billingDay = 17)
        spend(card, 500_00, at("2026-09-17"))
        spend(card, 1_200_00, at("2026-09-18"))
        spend(card, 200_00, at("2026-10-02"), Direction.REFUND)

        val summary = card(card)
        assertNull(summary.latest)
        assertEquals(CardCycle(day("2026-09-18"), day("2026-10-17")), summary.currentCycle)
        assertEquals(1_000_00L, summary.currentSpentPaise)
    }

    @Test
    fun aMissingStatementIsFilledInByTheBillingDay() = runTest {
        val card = addCard(billingDay = 1)
        saved(statement("2026-08-01", due = "2026-08-23"))
        saved(statement("2026-10-01", due = "2026-10-23"))

        assertEquals(CardCycle(day("2026-09-02"), day("2026-10-01")), latest(card).cycle)
    }

    @Test
    fun billingDayCanBeSetAndCleared() = runTest {
        val accounts = AccountRepository(lazyDb)
        val card = addCard()

        accounts.setBillingDay(card, 31)
        assertEquals(31, card(card).card.billingDay)
        accounts.setBillingDay(card, null)
        assertNull(card(card).card.billingDay)
        assertTrue(runCatching { accounts.setBillingDay(card, 0) }.isFailure)
    }

    @Test
    fun eachPaymentStatus() = runTest {
        val card = saved(statement()).cardId
        assertEquals(PaymentStatus.NOT_PAID, latest(card).status)

        pay(card, 1_000_00, at("2026-10-05"))
        assertEquals(PaymentStatus.PARTLY_PAID, latest(card).status)
        assertEquals(1_000_00L, latest(card).paidPaise)

        pay(card, 2_450_00, at("2026-10-10"))
        assertEquals(PaymentStatus.MINIMUM_PAID, latest(card).status)
        assertEquals(PaymentStatus.OVERDUE, latest(card, today = "2026-10-24").status)

        // Partial payments add up.
        pay(card, 31_849_72, at("2026-10-20"))
        assertEquals(PaymentStatus.PAID_IN_FULL, latest(card).status)
        assertEquals(3, latest(card).payments.size)
        assertEquals(PaymentStatus.PAID_IN_FULL, latest(card, today = "2026-10-24").status)
    }

    @Test
    fun aPaymentBeforeTheStatementDateDoesNotCount() = runTest {
        val card = saved(statement("2026-10-01")).cardId
        pay(card, 35_299_72, at("2026-10-01", hour = 23))

        assertEquals(PaymentStatus.NOT_PAID, latest(card).status)
        assertTrue(latest(card).payments.isEmpty())
    }

    @Test
    fun paymentsAfterTheNextStatementCountForThatOne() = runTest {
        val card = saved(statement("2026-09-01", due = "2026-09-23", total = 1_000_00)).cardId
        pay(card, 1_000_00, at("2026-09-20"))
        saved(statement("2026-10-01", due = "2026-10-23", total = 2_000_00))
        pay(card, 500_00, at("2026-10-03"))

        val (october, september) = card(card).statements
        assertEquals(PaymentStatus.PAID_IN_FULL, september.status)
        assertEquals(PaymentStatus.PARTLY_PAID, october.status)
        assertEquals(500_00L, october.paidPaise)
    }

    @Test
    fun aCredPaymentLinkedToPaymentReceivedCountsOnce() = runTest {
        val card = saved(statement()).cardId
        addCard(bank = "Axis", last4 = "5678")
        val amount = 35_299_72L
        importer.import(
            SmsTransaction(
                amountPaise = amount,
                direction = Direction.DEBIT,
                timestamp = at("2026-10-10"),
                bank = "HDFC",
                accountType = AccountType.BANK,
                accountLast4 = "9999",
                payee = "cred.club@axisb",
                referenceNo = null,
                rawSms = "Rs.$amount debited from A/c XX9999 to cred.club@axisb"
            )
        )
        importer.import(
            SmsTransaction(
                amountPaise = amount,
                direction = Direction.CREDIT,
                timestamp = at("2026-10-10") + MINUTES_5,
                bank = "ICICI",
                accountType = AccountType.CREDIT_CARD,
                accountLast4 = "1234",
                payee = null,
                referenceNo = null,
                rawSms = "Payment of Rs $amount has been received on your ICICI Credit Card XX1234"
            )
        )

        val statement = latest(card)
        assertEquals(1, statement.payments.size)
        assertEquals(amount, statement.paidPaise)
        assertEquals(PaymentStatus.PAID_IN_FULL, statement.status)
    }

    @Test
    fun aBankSideBillPaymentCountsOnlyWhenThereIsOneCard() = runTest {
        val card = saved(statement()).cardId
        importer.import(
            SmsTransaction(
                amountPaise = 5_000_00,
                direction = Direction.DEBIT,
                timestamp = at("2026-10-10"),
                bank = "HDFC",
                accountType = AccountType.BANK,
                accountLast4 = "9999",
                payee = "CRED",
                referenceNo = "512345678901",
                rawSms = "Rs.5000 debited from A/c XX9999 to CRED"
            )
        )
        assertEquals(5_000_00L, latest(card).paidPaise)

        addCard(bank = "Axis", last4 = "5678")
        assertEquals(0L, latest(card).paidPaise)
    }

    @Test
    fun theStatementGoesToTheCardWithMatchingDigits() = runTest {
        val other = addCard(bank = "HDFC", last4 = "1111")
        val mine = addCard(bank = "HDFC Bank", last4 = "4321")
        // A bank account with the same digits isn't a card.
        db.accountDao().insert(AccountEntity(0, "HDFC Savings", AccountType.BANK, "HDFC", "4321"))

        val result = saved(statement(bank = "HDFC", last4 = "4321"))

        assertEquals(mine, result.cardId)
        assertTrue(card(other).statements.isEmpty())
    }

    @Test
    fun withNoMatchingCardOneIsMade() = runTest {
        val result = saved(statement(bank = "Axis", last4 = "5678"))

        val card = db.accountDao().getById(result.cardId)!!
        assertEquals(
            listOf("Axis 5678", AccountType.CREDIT_CARD, "Axis", "5678"),
            listOf(card.name, card.type, card.bank, card.last4)
        )
    }

    @Test
    fun theSameStatementIsSavedOnceEvenAfterItMoved() = runTest {
        val first = saved(statement())
        assertEquals(
            StatementSaveResult.Duplicate(first.statementId),
            cards.save(statement())
        )

        val other = addCard(bank = "ICICI", last4 = "9876")
        cards.moveStatement(first.statementId, other)
        assertEquals(1, card(other).statements.size)
        assertTrue(card(first.cardId).statements.isEmpty())
        assertEquals(
            StatementSaveResult.Duplicate(first.statementId),
            cards.save(statement())
        )
    }

    @Test
    fun statementsMoveOnlyToCreditCards() = runTest {
        val first = saved(statement())
        val bank = db.accountDao().insert(AccountEntity(0, "Savings", AccountType.BANK, null, null))

        assertTrue(runCatching { cards.moveStatement(first.statementId, bank) }.isFailure)
    }

    @Test
    fun linksAreTakenOutBeforeSaving() = runTest {
        val body = "Your statement for Axis Bank Credit Card no. XX5678 is generated.\n" +
            "Due on: 02-11-26\nTotal amt: INR  Dr. 8695.00\nMin amt due: INR  Dr. 174.00\n" +
            "Pay at axis.bank.in/ccpaynow\n" +
            "To view/download statement: https://example.com/abc"
        val card = saved(statement(bank = "Axis", last4 = "5678", body = body)).cardId

        assertEquals(
            "Your statement for Axis Bank Credit Card no. XX5678 is generated.\n" +
                "Due on: 02-11-26\nTotal amt: INR  Dr. 8695.00\nMin amt due: INR  Dr. 174.00\n" +
                "Pay at\nTo view/download statement:",
            latest(card).statement.rawSms
        )
        assertEquals("Paid Rs.500.00/- at 10:30", SmsLinks.strip("Paid Rs.500.00/- at 10:30"))
    }

    @Test
    fun cycleSpendingIsExpensesMinusRefundsByCategory() = runTest {
        val card = saved(statement("2026-10-01")).cardId
        spend(card, 2_000_00, at("2026-09-15"))
        spend(card, 300_00, at("2026-09-20"), Direction.REFUND)
        spend(card, 900_00, at("2026-10-05"))
        spend(card, 999_00, at("2026-08-15"))

        val summary = card(card)
        assertEquals(1_700_00L, summary.latestSpentPaise)
        assertEquals(900_00L, summary.currentSpentPaise)
        val byCategory = cards.observeCycleSpending(summary.card, summary.latest!!.cycle, zone)
            .first()
        assertEquals(listOf(1_700_00L), byCategory.map { it.spentPaise })
    }

    @Test
    fun nextDueIsTheSoonestUnpaidBill() = runTest {
        val icici = saved(statement(due = "2026-10-23", total = 1_000_00)).cardId
        val axis = saved(statement(bank = "Axis", last4 = "5678", due = "2026-11-02")).cardId
        val hdfc = saved(statement(bank = "HDFC", last4 = "4321", due = "2026-10-20")).cardId
        pay(hdfc, 35_299_72, at("2026-10-05"))

        val today = day("2026-10-15")
        assertEquals(icici, cards.observeNextDue(today, zone).first()!!.card.id)

        pay(icici, 1_000_00, at("2026-10-06"))
        assertEquals(axis, cards.observeNextDue(today, zone).first()!!.card.id)

        pay(axis, 35_299_72, at("2026-10-07"))
        assertNull(cards.observeNextDue(today, zone).first())
    }

    @Test
    fun deletingACardMovesItsStatementsWithItsTransactions() = runTest {
        val first = saved(statement())
        val other = addCard(bank = "ICICI", last4 = "9876")

        AccountRepository(lazyDb).delete(first.cardId, moveTransactionsTo = other)

        assertEquals(1, card(other).statements.size)
    }

    private companion object {
        const val MINUTES_5 = 5 * 60_000L
    }
}
