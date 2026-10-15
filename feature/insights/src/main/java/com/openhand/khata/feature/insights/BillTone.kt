package com.openhand.khata.feature.insights

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.openhand.khata.core.model.PaymentStatus
import com.openhand.khata.core.model.StatementSummary
import com.openhand.khata.core.ui.Tone
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * How urgent a credit card bill looks (#155): its status colour on the Cards tab, the card's page
 * and Home. A bill that isn't paid in full is due soon within [DUE_SOON_DAYS] of its due date.
 */
enum class BillTone {
    PAID,
    PARTLY_PAID,
    DUE,
    DUE_SOON,
    OVERDUE;

    companion object {
        const val DUE_SOON_DAYS = 7L

        fun of(bill: StatementSummary, today: LocalDate): BillTone = when (bill.status) {
            PaymentStatus.PAID_IN_FULL -> PAID
            PaymentStatus.OVERDUE -> OVERDUE
            else -> when {
                ChronoUnit.DAYS.between(today, bill.statement.dueDate) <= DUE_SOON_DAYS ->
                    DUE_SOON
                bill.status == PaymentStatus.NOT_PAID -> DUE
                else -> PARTLY_PAID
            }
        }
    }
}

/** Green when paid, the accent while due, coral once it's close or late; blue part-paid. */
@Composable
internal fun BillTone.color(): Color = when (this) {
    BillTone.PARTLY_PAID -> MaterialTheme.colorScheme.tertiary
    else -> tone().color()
}

/** The same, as a [Tone] for chips and notices. */
internal fun BillTone.tone(): Tone = when (this) {
    BillTone.PAID -> Tone.POSITIVE
    BillTone.PARTLY_PAID -> Tone.NEUTRAL
    BillTone.DUE -> Tone.ACCENT
    BillTone.DUE_SOON, BillTone.OVERDUE -> Tone.WARNING
}
