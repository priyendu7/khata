package com.openhand.khata.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.TransactionFilter
import com.openhand.khata.core.ui.R as UiR

/**
 * One tag's or account's spending by category for its card's period: the same donut and legend
 * as the category card. Tapping a category, or See transactions, opens the list for it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BreakdownSheet(
    state: BreakdownState,
    onClose: () -> Unit,
    onOpenTransactions: (TransactionFilter) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            Text(
                stringResource(
                    R.string.insights_breakdown_title,
                    state.of.name(),
                    spanLabel(state.period, state.span)
                ),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                stringResource(
                    R.string.insights_breakdown_total,
                    Money.format(state.breakdown.totalPaise)
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.breakdown.isEmpty) {
                ChartEmpty(
                    stringResource(R.string.insights_nothing_spent_title),
                    stringResource(R.string.insights_nothing_spent_body)
                )
            } else {
                CategoryBreakdownChart(state.breakdown) { onOpenTransactions(state.filter(it)) }
            }
            FilledTonalButton(
                onClick = { onOpenTransactions(state.filter()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.insights_see_transactions))
            }
        }
    }
}

@Composable
private fun BreakdownOf.name(): String = when (this) {
    is BreakdownOf.OfTag -> tag?.name ?: stringResource(UiR.string.tag_untagged)
    is BreakdownOf.OfAccount -> accountLabel(account)
}
