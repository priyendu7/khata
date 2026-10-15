package com.openhand.khata.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.segmentCardColors

/**
 * Home's card for the next unpaid credit card bill (#155): "Axis ··5678 · ₹8,695 due 2 Nov · in
 * 18 days", in the error colours once it's overdue. Tapping it opens the card's page.
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
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = Segments.Single,
        colors = if (overdue) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        } else {
            segmentCardColors()
        }
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.home_bill_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(
                    R.string.home_bill_body,
                    cardLabel(bill.due.card),
                    Money.format(statement.totalPaise),
                    shortDate(statement.dueDate),
                    whenDue
                )
            )
            Text(
                statusLabel(bill.due.statement),
                style = MaterialTheme.typography.labelLarge,
                color = if (overdue) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    BillTone.of(bill.due.statement, bill.today).color()
                }
            )
        }
    }
}
