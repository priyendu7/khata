package com.openhand.khata.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.ui.NoticeCard
import com.openhand.khata.core.ui.R as UiR

/**
 * Home's card for the next unpaid credit card bill (#155): "Axis ··5678 · ₹8,695 due 2 Nov · in
 * 18 days", tinted by how close it is. Tapping it opens the card's page.
 */
@Composable
internal fun BillDueCard(bill: BillDue, onClick: () -> Unit) {
    val overdue = bill.daysLeft < 0
    val statement = bill.due.statement.statement
    val days = bill.daysLeft.toInt()
    val whenDue = when {
        overdue -> pluralStringResource(R.plurals.home_bill_overdue, -days, -days)
        days == 0 -> stringResource(R.string.home_bill_today)
        else -> pluralStringResource(R.plurals.home_bill_in_days, days, days)
    }
    NoticeCard(
        icon = painterResource(UiR.drawable.ic_credit_card),
        title = stringResource(R.string.home_bill_title),
        body = stringResource(
            R.string.home_bill_body,
            cardLabel(bill.due.card),
            Money.format(statement.totalPaise),
            shortDate(statement.dueDate),
            whenDue
        ),
        tone = BillTone.of(bill.due.statement, bill.today).tone(),
        onClick = onClick,
        action = statusLabel(bill.due.statement)
    )
}
