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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.Change
import com.openhand.khata.core.model.MonthlyComparison
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
    onOpenMonth: (YearMonth) -> Unit
) {
    var stacked by rememberSaveable { mutableStateOf(false) }
    val comparison = state.comparison
    ChartCard(stringResource(R.string.insights_monthly_title)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            listOf(InsightsViewModel.SHORT_MONTHS, InsightsViewModel.LONG_MONTHS).forEach {
                FilterChip(
                    selected = state.months == it,
                    onClick = { onSelectMonths(it) },
                    label = { Text(pluralStringResource(R.plurals.insights_months, it, it)) }
                )
            }
            FilterChip(
                selected = stacked,
                onClick = { stacked = !stacked },
                label = { Text(stringResource(R.string.insights_stack_by_category)) }
            )
        }
        if (comparison.bars.all { it.spentPaise == 0L && it.incomePaise == 0L }) {
            ChartEmpty(
                stringResource(R.string.insights_no_months_title),
                stringResource(R.string.insights_no_months_body)
            )
        } else {
            MonthlyBars(comparison, stacked, onOpenMonth)
            MonthlyLegend(comparison, stacked)
            Changes(comparison)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthlyLegend(comparison: MonthlyComparison, stacked: Boolean) {
    val theme = chartTheme()
    val spending = if (stacked) {
        comparison.stacked.map {
            categoryName(it.name, it.seedKey) to theme.categoryColor(it.color)
        } + (stringResource(R.string.insights_other) to theme.other)
    } else {
        listOf(stringResource(R.string.insights_spent) to theme.spending)
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        (
            spending + (
                stringResource(
                    R.string.insights_income
                ) to theme.income
                )
            ).forEach { (name, color) ->
            LegendKey(name, color)
        }
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

/** The last month against the one before, overall and for each stacked category. */
@Composable
private fun Changes(comparison: MonthlyComparison) {
    val bars = comparison.bars
    if (bars.size < 2 || (comparison.change == null && comparison.categoryChanges.isEmpty())) return
    val format = DateTimeFormatter.ofPattern("LLLL", LocalConfiguration.current.locales[0])
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(
                R.string.insights_change_title,
                bars.last().month.format(format),
                bars[bars.size - 2].month.format(format)
            ),
            style = MaterialTheme.typography.titleSmall
        )
        comparison.change?.let { ChangeRow(stringResource(R.string.insights_change_all), it) }
        comparison.categoryChanges.forEach {
            ChangeRow(categoryName(it.category.name, it.category.seedKey), it.change)
        }
    }
}

@Composable
private fun ChangeRow(name: String, change: Change) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(changeLabel(change), style = MaterialTheme.typography.labelLarge)
    }
}

/** "+18%", "−5%" or "New", with the number written for the phone's language. */
@Composable
internal fun changeLabel(change: Change): String = when (change) {
    Change.New -> stringResource(R.string.insights_change_new)
    is Change.Percent -> {
        val number = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
            .format(change.value.absoluteValue)
        stringResource(
            if (change.value < 0) R.string.insights_change_down else R.string.insights_change_up,
            number
        )
    }
}
