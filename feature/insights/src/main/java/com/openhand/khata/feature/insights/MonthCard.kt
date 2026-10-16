package com.openhand.khata.feature.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.TransactionFilter
import com.openhand.khata.core.ui.AmountText
import com.openhand.khata.core.ui.StatusChip
import com.openhand.khata.core.ui.SurfaceCard
import com.openhand.khata.core.ui.Tone
import com.openhand.khata.core.ui.incomeColor

/**
 * This month's spending, large, against last month's; income below. Tapping either opens the
 * transactions behind it (#164): spending is expenses and refunds, as its figure counts both.
 */
@Composable
internal fun MonthCard(summary: HomeSummary, onOpenTransactions: (TransactionFilter) -> Unit) {
    fun open(vararg directions: Direction) = onOpenTransactions(
        TransactionFilter(
            directions = directions.toSet(),
            from = summary.monthFrom,
            until = summary.monthUntil
        )
    )
    SurfaceCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.clickable(
                onClickLabel = stringResource(R.string.home_see_spent)
            ) { open(Direction.DEBIT, Direction.REFUND) }
        ) {
            MonthSpent(summary)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.home_see_income)) {
                    open(Direction.CREDIT)
                }
        ) {
            MonthIncome(summary)
        }
    }
}

@Composable
private fun MonthSpent(summary: HomeSummary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.home_spent),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        StatusChip(stringResource(R.string.home_this_month), Tone.NEUTRAL)
    }
    AmountText(summary.month.spentPaise)
    summary.change?.let { StatusChip(changeText(it), it.tone()) }
}

@Composable
private fun MonthIncome(summary: HomeSummary) {
    Text(
        stringResource(R.string.home_income),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        Money.format(summary.month.incomePaise),
        style = MaterialTheme.typography.headlineSmall,
        color = if (summary.month.incomePaise > 0) incomeColor() else Color.Unspecified,
        maxLines = 1,
        softWrap = false
    )
}

/** Less spent is good news; more is a warning. */
private fun MonthChange.tone(): Tone = when (this) {
    is MonthChange.More -> Tone.WARNING
    is MonthChange.Less -> Tone.POSITIVE
    MonthChange.Same -> Tone.NEUTRAL
}

@Composable
private fun changeText(change: MonthChange): String = when (change) {
    is MonthChange.More -> stringResource(
        R.string.home_more_than_last_month,
        Money.format(change.paise),
        change.percent
    )
    is MonthChange.Less -> stringResource(
        R.string.home_less_than_last_month,
        Money.format(change.paise),
        change.percent
    )
    MonthChange.Same -> stringResource(R.string.home_same_as_last_month)
}
