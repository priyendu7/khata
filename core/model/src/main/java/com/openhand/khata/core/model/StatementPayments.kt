package com.openhand.khata.core.model

import java.time.LocalDate

/**
 * Which statement each payment into a credit card counts toward (#166). Each counts toward one
 * statement only:
 * - a payment of a statement's total or minimum made up to [EARLY_DAYS] before its statement date
 *   pays that statement, as people often pay after the bank's email, before the SMS;
 * - otherwise the latest statement whose window has begun: its statement date, or [EARLY_DAYS]
 *   before it when that's only the day the SMS arrived, as the real date may be earlier.
 */
object StatementPayments {
    const val EARLY_DAYS = 5L

    /**
     * [payments] by the id of the statement they pay, each oldest first; [dateOf] is a payment's
     * local date. Payments before the first statement's window pay none of them.
     */
    fun allocate(
        statements: List<CardStatement>,
        payments: List<CardPayment>,
        dateOf: (CardPayment) -> LocalDate
    ): Map<Long, List<CardPayment>> {
        val oldestFirst = statements.sortedWith(compareBy({ it.statementDate }, { it.id }))
        return payments.sortedWith(compareBy({ it.timestamp }, { it.transactionId }))
            .mapNotNull { payment ->
                val date = dateOf(payment)
                val statement = oldestFirst.firstOrNull { paysEarly(it, payment, date) }
                    ?: oldestFirst.lastOrNull { windowStart(it) <= date }
                statement?.let { it.id to payment }
            }
            .groupBy({ it.first }, { it.second })
    }

    private fun paysEarly(statement: CardStatement, payment: CardPayment, date: LocalDate) =
        date < statement.statementDate &&
            date >= statement.statementDate.minusDays(EARLY_DAYS) &&
            (
                payment.amountPaise == statement.totalPaise ||
                    payment.amountPaise == statement.minDuePaise
                )

    private fun windowStart(statement: CardStatement): LocalDate = if (statement.dateFromSms) {
        statement.statementDate
    } else {
        statement.statementDate.minusDays(EARLY_DAYS)
    }
}
