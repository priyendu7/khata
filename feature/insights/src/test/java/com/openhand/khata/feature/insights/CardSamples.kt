package com.openhand.khata.feature.insights

import com.openhand.khata.core.data.CardSummary
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.CardCycle
import com.openhand.khata.core.model.CardPayment
import com.openhand.khata.core.model.CardStatement
import com.openhand.khata.core.model.PaymentStatus
import com.openhand.khata.core.model.StatementSummary
import java.time.LocalDate
import java.time.ZoneId

/** Cards and bills for the Cards screens' tests (#155), on [TODAY]. */
object CardSamples {
    val TODAY: LocalDate = LocalDate.of(2026, 10, 15)

    fun creditCard(id: Long, name: String, last4: String = "1234") =
        Account(id, name, AccountType.CREDIT_CARD, bank = name, last4 = last4)

    /** A bill dated a month before [due] less 22 days, with [paid] paid in one payment. */
    fun bill(
        id: Long,
        cardId: Long,
        total: Long,
        due: LocalDate,
        status: PaymentStatus,
        paid: Long = 0,
        minDue: Long? = null
    ): StatementSummary {
        val statementDate = due.minusDays(STATEMENT_TO_DUE_DAYS)
        val payments = if (paid > 0) {
            listOf(CardPayment(id * PAYMENT_IDS, paid, millis(statementDate.plusDays(2))))
        } else {
            emptyList()
        }
        return StatementSummary(
            statement = CardStatement(id, cardId, statementDate, due, total, minDue, "SMS"),
            cycle = CardCycle(statementDate.minusMonths(1).plusDays(1), statementDate),
            payments = payments,
            status = status
        )
    }

    fun summary(
        card: Account,
        statements: List<StatementSummary> = emptyList(),
        latestSpent: Long? = statements.firstOrNull()?.let { 0L },
        currentSpent: Long = 0
    ) = CardSummary(
        card = card,
        statements = statements,
        latestSpentPaise = latestSpent,
        currentCycle = CardCycle(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 11, 1)),
        currentSpentPaise = currentSpent
    )

    fun millis(day: LocalDate): Long =
        day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private const val STATEMENT_TO_DUE_DAYS = 22L
    private const val PAYMENT_IDS = 100L
}
