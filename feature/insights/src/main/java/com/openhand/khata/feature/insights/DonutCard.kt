package com.openhand.khata.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.CategoryBreakdown
import com.openhand.khata.core.model.CategorySpend
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.ui.categoryName

/** The category donut, its period switch and its legend (PRD feature 5). */
@Composable
internal fun DonutCard(
    donut: DonutState,
    onSelectPeriod: (ChartPeriod) -> Unit,
    onStepPeriod: (Int) -> Unit,
    onSelectPast: (Int) -> Unit,
    onOpenCategory: (Long?) -> Unit
) {
    ChartCard(stringResource(R.string.insights_by_category)) {
        PeriodChips(donut.period, onSelectPeriod)
        PeriodStepper(donut, onStepPeriod, onSelectPast)
        if (donut.breakdown.isEmpty) {
            ChartEmpty(
                stringResource(R.string.insights_nothing_spent_title),
                stringResource(R.string.insights_nothing_spent_body)
            )
        } else {
            CategoryBreakdownChart(donut.breakdown, onOpenCategory)
        }
    }
}

@Composable
internal fun PeriodChips(selected: ChartPeriod, onSelect: (ChartPeriod) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    ) {
        ChartPeriod.entries.forEach { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onSelect(period) },
                label = { Text(stringResource(period.label())) }
            )
        }
    }
}

private fun ChartPeriod.label(): Int = when (this) {
    ChartPeriod.WEEK -> R.string.insights_period_week
    ChartPeriod.MONTH -> R.string.insights_period_month
    ChartPeriod.YEAR -> R.string.insights_period_year
    ChartPeriod.CUSTOM -> R.string.insights_period_custom
}

/** A legend row: a slice (or an entry left out of the donut) and what tapping it opens. */
internal data class LegendEntry<K>(
    val key: K,
    val name: String,
    val color: Color,
    val paise: Long,
    /** Percent of the donut, or null for an entry that isn't in it. */
    val share: Int?
)

@Composable
internal fun CategoryBreakdownChart(breakdown: CategoryBreakdown, onOpenCategory: (Long?) -> Unit) {
    val theme = chartTheme()
    val charted = breakdown.chartedPaise
    val slices = breakdown.slices.map { it.toEntry(theme, charted) } +
        listOfNotNull(
            breakdown.otherPaise.takeIf { it > 0 }?.let {
                LegendEntry<Long?>(
                    null,
                    stringResource(R.string.insights_other),
                    theme.other,
                    it,
                    shareOf(it, charted)
                )
            }
        )
    SpendingDonut(
        slices = slices,
        refunded = breakdown.refunded.map { it.toEntry(theme, null) },
        totalPaise = breakdown.totalPaise,
        onOpen = onOpenCategory,
        theme = theme
    )
}

/**
 * A donut of [slices] with [totalPaise] in the middle, its legend, and then the [refunded] entries
 * (more refunds than spending) that a donut can't draw. Tapping any of them calls [onOpen].
 */
@Composable
internal fun <K> SpendingDonut(
    slices: List<LegendEntry<K>>,
    refunded: List<LegendEntry<K>>,
    totalPaise: Long,
    onOpen: (K) -> Unit,
    theme: ChartTheme = chartTheme()
) {
    val donutSlices = slices.map {
        DonutSlice(
            it.paise,
            it.color,
            stringResource(
                R.string.insights_slice_description,
                it.name,
                Money.format(it.paise),
                it.share ?: 0
            )
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CategoryDonut(
            slices = donutSlices,
            label = stringResource(R.string.insights_spent),
            amount = Money.format(totalPaise),
            onSliceClick = { onOpen(slices[it].key) },
            theme = theme,
            modifier = Modifier
                .fillMaxWidth(DONUT_WIDTH)
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 8.dp)
        )
        slices.forEach { LegendRow(it) { onOpen(it.key) } }
        if (refunded.isNotEmpty()) RefundedSection(refunded, onOpen)
    }
}

/** Entries with more refunds than spending: a donut can't draw them, so they're listed. */
@Composable
private fun <K> RefundedSection(entries: List<LegendEntry<K>>, onOpen: (K) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            stringResource(R.string.insights_refunded_title),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            stringResource(R.string.insights_refunded_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    entries.forEach { LegendRow(it) { onOpen(it.key) } }
}

@Composable
private fun CategorySpend.toEntry(theme: ChartTheme, charted: Long?) = LegendEntry<Long?>(
    key = category.id,
    name = categoryName(category.name, category.seedKey),
    color = theme.categoryColor(category.color),
    paise = spentPaise,
    share = charted?.let { shareOf(spentPaise, it) }
)

/** A legend line; TalkBack reads it as name, amount and share, and it opens the transactions. */
@Composable
private fun LegendRow(entry: LegendEntry<*>, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp)
    ) {
        Box(Modifier.size(12.dp).background(entry.color, CircleShape))
        Text(
            entry.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Money.format(entry.paise),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                softWrap = false
            )
            entry.share?.let {
                Text(
                    stringResource(R.string.insights_share, it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** [paise] as a whole percent of [total], rounded; integer maths, as for all money. */
internal fun shareOf(paise: Long, total: Long): Int =
    if (total <= 0) 0 else ((paise * PERCENT + total / 2) / total).toInt()

private const val PERCENT = 100
private const val DONUT_WIDTH = 0.7f
