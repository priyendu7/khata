package com.openhand.khata.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.PaymentStatus
import com.openhand.khata.core.model.StatementSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** "ICICI ··1234": the card's name, and its last 4 digits unless the name has them. */
@Composable
internal fun cardLabel(card: Account): String {
    val last4 = card.shownLast4() ?: return card.name
    return stringResource(R.string.cards_card_last4, card.name, last4)
}

/** The card's last 4 digits, unless its name already has them. */
internal fun Account.shownLast4(): String? = last4?.takeUnless { name.contains(it) }

/** "Not paid", "Partly paid (₹5,000 of ₹35,300)" and so on. */
@Composable
internal fun statusLabel(bill: StatementSummary): String = when (bill.status) {
    PaymentStatus.PAID_IN_FULL -> stringResource(R.string.cards_status_paid)
    PaymentStatus.MINIMUM_PAID -> stringResource(R.string.cards_status_minimum)
    PaymentStatus.PARTLY_PAID -> stringResource(
        R.string.cards_status_partly,
        Money.format(bill.paidPaise),
        Money.format(bill.statement.totalPaise)
    )
    PaymentStatus.NOT_PAID -> stringResource(R.string.cards_status_not_paid)
    PaymentStatus.OVERDUE -> stringResource(R.string.cards_status_overdue)
}

/** "₹35,300 due 23 Oct". */
@Composable
internal fun dueLabel(bill: StatementSummary): String = stringResource(
    R.string.cards_due,
    Money.format(bill.statement.totalPaise),
    shortDate(bill.statement.dueDate)
)

/** "in 7 days", "today" or "3 days overdue", for a bill due in [days]. */
@Composable
internal fun dueIn(days: Long): String {
    val n = days.toInt()
    return when {
        n < 0 -> pluralStringResource(R.plurals.home_bill_overdue, -n, -n)
        n == 0 -> stringResource(R.string.home_bill_today)
        else -> pluralStringResource(R.plurals.home_bill_in_days, n, n)
    }
}

/** What's still owed on a bill: never below zero, whatever was overpaid. */
internal val StatementSummary.owedPaise: Long
    get() = (statement.totalPaise - paidPaise).coerceAtLeast(0)

/** "23 Oct", in the app's language. */
@Composable
internal fun shortDate(date: LocalDate): String = date.format(formatter("d MMM"))

/** "23 Oct 2026". */
@Composable
internal fun longDate(date: LocalDate): String = date.format(formatter("d MMM yyyy"))

@Composable
private fun formatter(pattern: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(pattern, LocalConfiguration.current.locales[0])
