package com.openhand.khata.core.model

import java.time.LocalDate

/** A credit card statement (#154): what the bank says is due on [accountId], and by when. */
data class CardStatement(
    val id: Long = 0,
    val accountId: Long,
    val statementDate: LocalDate,
    val dueDate: LocalDate,
    val totalPaise: Long,
    /** Null when the SMS didn't give a minimum. */
    val minDuePaise: Long?,
    /** The SMS, with its links taken out. */
    val rawSms: String
)

/** Money paid into a credit card: one transfer, counted once even when both sides are saved. */
data class CardPayment(val transactionId: Long, val amountPaise: Long, val timestamp: Long)

/** Whether a statement is paid, from the payments made after its statement date. */
enum class PaymentStatus {
    PAID_IN_FULL,
    MINIMUM_PAID,
    PARTLY_PAID,
    NOT_PAID,

    /** Past the due date and not paid in full. */
    OVERDUE;

    val isPaid: Boolean get() = this == PAID_IN_FULL

    companion object {
        fun of(
            totalPaise: Long,
            minDuePaise: Long?,
            paidPaise: Long,
            dueDate: LocalDate,
            today: LocalDate
        ): PaymentStatus = when {
            paidPaise >= totalPaise -> PAID_IN_FULL
            today > dueDate -> OVERDUE
            paidPaise > 0 && minDuePaise != null && paidPaise >= minDuePaise -> MINIMUM_PAID
            paidPaise > 0 -> PARTLY_PAID
            else -> NOT_PAID
        }
    }
}

/** A statement with the cycle it bills, the payments towards it and so its [status]. */
data class StatementSummary(
    val statement: CardStatement,
    val cycle: CardCycle,
    /** Oldest first. */
    val payments: List<CardPayment>,
    val status: PaymentStatus
) {
    val paidPaise: Long get() = payments.sumOf { it.amountPaise }
}
