package com.openhand.khata.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.Totals
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.TransactionRow
import com.openhand.khata.core.ui.dayLabel

@Composable
internal fun DayList(days: List<DaySection>, onOpen: (Long) -> Unit) {
    // Room for the "+" button so it never covers the last row.
    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp), modifier = Modifier.fillMaxSize()) {
        days.forEach { day ->
            stickyHeader(key = "day-${day.date}", contentType = "day") { DayHeader(day) }
            itemsIndexed(
                day.items,
                key = { _, row -> row.id },
                contentType = { _, _ -> "transaction" }
            ) { index, item ->
                // Inset here rather than in the list, so the sticky header spans the screen.
                Box(Modifier.padding(horizontal = Segments.Inset)) {
                    TransactionRow(item, index, day.items.size) { onOpen(item.id) }
                }
            }
        }
    }
}

/** What the days add up to, spent and received, or blank when neither (only transfers). */
@Composable
private fun totalsLabel(totals: Totals): String = listOfNotNull(
    totals.spentPaise.takeIf { it > 0 }?.let {
        stringResource(R.string.day_spent, Money.format(it))
    },
    totals.incomePaise.takeIf { it > 0 }?.let {
        stringResource(R.string.day_received, Money.format(it))
    }
).joinToString(" · ")

@Composable
internal fun ListTotals(days: List<DaySection>) {
    val totals = Totals(
        spentPaise = days.sumOf { it.totals.spentPaise },
        incomePaise = days.sumOf { it.totals.incomePaise }
    )
    val label = totalsLabel(totals)
    if (label.isNotEmpty()) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun DayHeader(day: DaySection) {
    val totals = totalsLabel(day.totals)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // Hides the rows scrolling under it.
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 8.dp)
    ) {
        Text(
            dayLabel(day.date),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            totals,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
