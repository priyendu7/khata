package com.openhand.khata.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.Change
import com.openhand.khata.core.model.MonthlyComparison
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.SegmentedTabs
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.Tone
import com.openhand.khata.core.ui.categoriesLabel
import com.openhand.khata.core.ui.categoryName
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

@Composable
internal fun HeatmapCard(state: HeatmapState, onOpenDay: (LocalDate) -> Unit) {
    val theme = chartTheme()
    ChartCard(stringResource(R.string.insights_heatmap_title)) {
        CalendarHeatmap(state, onOpenDay, theme = theme)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(R.string.insights_less),
                style = MaterialTheme.typography.labelSmall
            )
            theme.heat.forEach {
                Box(Modifier.size(12.dp).background(it, RoundedCornerShape(2.dp)))
            }
            Text(
                stringResource(R.string.insights_more),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
internal fun MonthlyCard(
    state: MonthlyState,
    onSelectMonths: (Int) -> Unit,
    onPickCategories: (Set<Long>) -> Unit,
    onOpenMonth: (YearMonth) -> Unit
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val comparison = state.comparison
    val picked = state.categories.filter { it.id in state.picked }
    ChartCard(stringResource(R.string.insights_monthly_title)) {
        SegmentedTabs(
            options = listOf(InsightsViewModel.SHORT_MONTHS, InsightsViewModel.LONG_MONTHS),
            selected = state.months,
            label = { pluralStringResource(R.plurals.insights_months, it, it) },
            onSelect = onSelectMonths,
            trackColor = MaterialTheme.colorScheme.background
        )
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            FilterChip(
                selected = picked.isNotEmpty(),
                onClick = { picking = true },
                shape = Segments.SmallShape,
                label = {
                    Text(
                        categoriesLabel(picked)
                            ?: stringResource(R.string.insights_categories_all),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingIcon = {
                    Icon(
                        painterResource(UiR.drawable.ic_arrow_drop_down),
                        contentDescription = null
                    )
                }
            )
        }
        if (comparison.bars.all { it.spentPaise == 0L && it.incomePaise == 0L }) {
            ChartEmpty(
                stringResource(R.string.insights_no_months_title),
                stringResource(
                    if (comparison.picked) {
                        R.string.insights_no_months_picked_body
                    } else {
                        R.string.insights_no_months_body
                    }
                )
            )
        } else {
            MonthlyBars(comparison, onOpenMonth)
            MonthlyLegend(comparison)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Changes(comparison)
        }
    }
    if (picking) {
        CategoriesDialog(
            categories = state.categories,
            picked = state.picked,
            onDone = {
                onPickCategories(it)
                picking = false
            },
            onDismiss = { picking = false }
        )
    }
}

/** The stacked categories, then "Other" and income unless categories are picked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthlyLegend(comparison: MonthlyComparison) {
    val theme = chartTheme()
    val categories = comparison.stacked.map {
        categoryName(it.name, it.seedKey) to theme.categoryColor(it.color)
    }
    val keys = if (comparison.picked) {
        categories
    } else {
        categories + listOf(
            stringResource(R.string.insights_other) to theme.other,
            stringResource(R.string.insights_income) to theme.income
        )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        keys.forEach { (name, color) -> LegendKey(name, color) }
    }
}

@Composable
private fun LegendKey(name: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(name, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * The last month against the one before, overall (or the picked categories together) and for each
 * stacked category.
 */
@Composable
private fun Changes(comparison: MonthlyComparison) {
    val bars = comparison.bars
    if (bars.size < 2 || (comparison.change == null && comparison.categoryChanges.isEmpty())) return
    val format = DateTimeFormatter.ofPattern("LLLL", LocalConfiguration.current.locales[0])
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(
                R.string.insights_change_title,
                bars.last().month.format(format),
                bars[bars.size - 2].month.format(format)
            ),
            style = MaterialTheme.typography.titleSmall
        )
        val total = if (comparison.picked) {
            R.string.insights_change_picked
        } else {
            R.string.insights_change_all
        }
        comparison.change?.let { ChangeRow(stringResource(total), it, overall = true) }
        comparison.categoryChanges.forEach {
            ChangeRow(categoryName(it.category.name, it.category.seedKey), it.change)
        }
    }
}

/** A name and its change: less spent in green, more in the warning colour. */
@Composable
private fun ChangeRow(name: String, change: Change, overall: Boolean = false) {
    val tone = when {
        change is Change.Percent && change.value < 0 -> Tone.POSITIVE
        change is Change.Percent && change.value > 0 -> Tone.WARNING
        else -> Tone.NEUTRAL
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (overall) FontWeight.Bold else null,
            modifier = Modifier.weight(1f)
        )
        Text(changeLabel(change), style = MaterialTheme.typography.labelLarge, color = tone.color())
    }
}

/** "↑ 18%", "↓ 5%", "No change" or "New", with the number written for the phone's language. */
@Composable
internal fun changeLabel(change: Change): String = when (change) {
    Change.New -> stringResource(R.string.insights_change_new)
    Change.Percent(0) -> stringResource(R.string.insights_change_none)
    is Change.Percent -> {
        val number = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
            .format(change.value.absoluteValue)
        stringResource(
            if (change.value < 0) R.string.insights_change_down else R.string.insights_change_up,
            number
        )
    }
}
