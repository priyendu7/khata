package com.openhand.khata.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.ui.DateRangeDialog
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.ScreenTitle
import java.time.LocalDate
import java.time.YearMonth

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
    val heatmap by viewModel.heatmap.collectAsStateWithLifecycle()
    val monthly by viewModel.monthly.collectAsStateWithLifecycle()
    var pickingDates by rememberSaveable { mutableStateOf(false) }
    InsightsContent(
        donut = donut,
        heatmap = heatmap,
        monthly = monthly,
        onSelectPeriod = {
            if (it == ChartPeriod.CUSTOM) pickingDates = true else viewModel.selectPeriod(it)
        },
        onOpenCategory = { id -> donut?.let { onOpenTransactions(id, it.from, it.until) } },
        onOpenDay = { day ->
            val (from, until) = viewModel.rangeOf(day)
            onOpenTransactions(null, from, until)
        },
        onSelectMonths = viewModel::selectMonths,
        onOpenMonth = { month ->
            val (from, until) = viewModel.rangeOf(month)
            onOpenTransactions(null, from, until)
        },
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

/**
 * The three charts: spending by category for a period ([onOpenCategory] gets null for "Other"),
 * by day over the last 12 months, and month by month. Each shows nothing until its first load.
 */
@Composable
fun InsightsContent(
    donut: DonutState?,
    heatmap: HeatmapState?,
    monthly: MonthlyState?,
    onSelectPeriod: (ChartPeriod) -> Unit,
    onOpenCategory: (Long?) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onSelectMonths: (Int) -> Unit,
    onOpenMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        ScreenTitle(stringResource(UiR.string.nav_insights))
        donut?.let { DonutCard(it, onSelectPeriod, onOpenCategory) }
        heatmap?.let { HeatmapCard(it, onOpenDay) }
        monthly?.let { MonthlyCard(it, onSelectMonths, onOpenMonth) }
        Spacer(Modifier.height(8.dp))
    }
}

/** A titled card holding one chart. */
@Composable
internal fun ChartCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** What a chart shows when there's nothing to draw. */
@Composable
internal fun ChartEmpty(title: String, body: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
    ) {
        Icon(
            painterResource(UiR.drawable.ic_insights),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
