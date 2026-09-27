package com.openhand.khata.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.model.CategoryBreakdown
import com.openhand.khata.core.model.CategorySpend
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.ui.DateRangeDialog
import com.openhand.khata.core.ui.EmptyState
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.ScreenTitle
import com.openhand.khata.core.ui.categoryName
import java.time.format.DateTimeFormatter

/**
 * Insights tab, wired to its [InsightsViewModel]. [onOpenTransactions] opens the transactions of a
 * category (null for all of them) between `from` and `until`.
 */
@Composable
fun InsightsScreen(
    onOpenTransactions: (categoryId: Long?, from: Long, until: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val donut by viewModel.donut.collectAsStateWithLifecycle()
    var pickingDates by rememberSaveable { mutableStateOf(false) }
    InsightsContent(
        donut = donut,
        onSelectPeriod = {
            if (it == ChartPeriod.CUSTOM) pickingDates = true else viewModel.selectPeriod(it)
        },
        onOpenCategory = { id -> donut?.let { onOpenTransactions(id, it.from, it.until) } },
        modifier = modifier
    )
    if (pickingDates) {
        DateRangeDialog(
            start = donut?.span?.first,
            end = donut?.span?.last,
            onPick = { first, last ->
                viewModel.selectRange(first, last)
                pickingDates = false
            },
            onDismiss = { pickingDates = false }
        )
    }
}

/** The period switch and the category donut. [onOpenCategory] gets null for "Other". */
@Composable
fun InsightsContent(
    donut: DonutState?,
    onSelectPeriod: (ChartPeriod) -> Unit,
    onOpenCategory: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(stringResource(UiR.string.nav_insights))
        PeriodChips(donut?.period ?: ChartPeriod.MONTH, onSelectPeriod)
        // Nothing until the first load, so the chart never flashes empty.
        if (donut == null) return@Column
        Text(
            spanLabel(donut.span),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (donut.breakdown.isEmpty) {
            EmptyState(
                icon = painterResource(UiR.drawable.ic_insights),
                title = stringResource(R.string.insights_nothing_spent_title),
                body = stringResource(R.string.insights_nothing_spent_body)
            )
        } else {
            CategoryCard(donut.breakdown, onOpenCategory)
        }
    }
}

@Composable
private fun PeriodChips(selected: ChartPeriod, onSelect: (ChartPeriod) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
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

@Composable
private fun spanLabel(span: DateSpan): String {
    val format = DateTimeFormatter.ofPattern("d MMM yyyy", LocalConfiguration.current.locales[0])
    val first = span.first.format(format)
    return if (span.first == span.last) first else first + " – " + span.last.format(format)
}

/** A legend row: a slice (or a category left out of the donut) and where tapping it goes. */
private data class LegendEntry(
    val categoryId: Long?,
    val name: String,
    val color: Color,
    val paise: Long,
    /** Percent of the donut, or null for a category that isn't in it. */
    val share: Int?
)

@Composable
private fun CategoryCard(breakdown: CategoryBreakdown, onOpenCategory: (Long?) -> Unit) {
    val theme = chartTheme()
    val charted = breakdown.chartedPaise
    val slices = breakdown.slices.map { it.toEntry(theme, charted) } +
        listOfNotNull(
            breakdown.otherPaise.takeIf { it > 0 }?.let {
                LegendEntry(
                    null,
                    stringResource(R.string.insights_other),
                    theme.other,
                    it,
                    shareOf(it, charted)
                )
            }
        )
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
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.insights_by_category),
                style = MaterialTheme.typography.titleMedium
            )
            CategoryDonut(
                slices = donutSlices,
                label = stringResource(R.string.insights_spent),
                amount = Money.format(breakdown.totalPaise),
                onSliceClick = { onOpenCategory(slices[it].categoryId) },
                theme = theme,
                modifier = Modifier
                    .fillMaxWidth(DONUT_WIDTH)
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp)
            )
            slices.forEach { LegendRow(it) { onOpenCategory(it.categoryId) } }
            if (breakdown.refunded.isNotEmpty()) {
                RefundedSection(breakdown.refunded.map { it.toEntry(theme, null) }, onOpenCategory)
            }
        }
    }
}

/** Categories with more refunds than spending: a donut can't draw them, so they're listed. */
@Composable
private fun RefundedSection(entries: List<LegendEntry>, onOpenCategory: (Long?) -> Unit) {
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
    entries.forEach { LegendRow(it) { onOpenCategory(it.categoryId) } }
}

@Composable
private fun CategorySpend.toEntry(theme: ChartTheme, charted: Long?) = LegendEntry(
    categoryId = category.id,
    name = categoryName(category.name, category.seedKey),
    color = theme.categoryColor(category.color),
    paise = spentPaise,
    share = charted?.let { shareOf(spentPaise, it) }
)

/** A legend line; TalkBack reads it as name, amount and share, and it opens the transactions. */
@Composable
private fun LegendRow(entry: LegendEntry, onClick: () -> Unit) {
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
