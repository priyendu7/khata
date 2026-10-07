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
import com.openhand.khata.core.model.TransactionFilter
import com.openhand.khata.core.ui.DateRangeDialog
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.ScreenTitle
import java.time.LocalDate
import java.time.YearMonth

/**
 * Insights tab, wired to its [InsightsViewModel]. [onOpenTransactions] opens the transactions list
 * with a filter: a category, tag or both (or neither) over a period.
 */
@Composable
fun InsightsScreen(
    onOpenTransactions: (TransactionFilter) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val donut by viewModel.donut.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val tagBreakdown by viewModel.tagBreakdown.collectAsStateWithLifecycle()
    val heatmap by viewModel.heatmap.collectAsStateWithLifecycle()
    val monthly by viewModel.monthly.collectAsStateWithLifecycle()
    // The card whose custom dates are being picked, if any.
    var pickingDates by rememberSaveable { mutableStateOf<PeriodCard?>(null) }
    fun selectPeriod(period: ChartPeriod, card: PeriodCard) {
        if (period ==
            ChartPeriod.CUSTOM
        ) {
            pickingDates = card
        } else {
            viewModel.selectPeriod(period, card)
        }
    }
    fun openRange(range: Pair<Long, Long>) =
        onOpenTransactions(TransactionFilter(from = range.first, until = range.second))
    InsightsContent(
        donut = donut,
        heatmap = heatmap,
        monthly = monthly,
        onSelectPeriod = { selectPeriod(it, PeriodCard.CATEGORIES) },
        onStepPeriod = viewModel::stepPeriod,
        onSelectPast = viewModel::selectPast,
        onOpenCategory = { id ->
            donut?.let {
                onOpenTransactions(
                    TransactionFilter(categoryId = id, from = it.from, until = it.until)
                )
            }
        },
        onOpenDay = { openRange(viewModel.rangeOf(it)) },
        onSelectMonths = viewModel::selectMonths,
        onOpenMonth = { openRange(viewModel.rangeOf(it)) },
        tags = tags,
        tagBreakdown = tagBreakdown,
        tagActions = TagActions(
            onSelectPeriod = { selectPeriod(it, PeriodCard.TAGS) },
            onStepPeriod = { viewModel.stepPeriod(it, PeriodCard.TAGS) },
            onSelectPast = { viewModel.selectPast(it, PeriodCard.TAGS) },
            onOpenTag = viewModel::openTag,
            onCloseTag = viewModel::closeTag,
            onOpenTransactions = onOpenTransactions
        ),
        modifier = modifier
    )
    pickingDates?.let { card ->
        val span = if (card == PeriodCard.TAGS) tags?.span else donut?.span
        DateRangeDialog(
            start = span?.first,
            end = span?.last,
            onPick = { first, last ->
                viewModel.selectRange(first, last, card)
                pickingDates = null
            },
            onDismiss = { pickingDates = null }
        )
    }
}

/**
 * The charts: spending by category for a period ([onOpenCategory] gets null for "Other";
 * [onStepPeriod] gets -1 for back and 1 for forward, [onSelectPast] how many periods back), by
 * tag for its own period (with [tagBreakdown] open over it), by day over the last 12 months, and
 * month by month. Each shows nothing until its first load.
 */
@Composable
fun InsightsContent(
    donut: DonutState?,
    heatmap: HeatmapState?,
    monthly: MonthlyState?,
    onSelectPeriod: (ChartPeriod) -> Unit,
    onStepPeriod: (Int) -> Unit,
    onSelectPast: (Int) -> Unit,
    onOpenCategory: (Long?) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onSelectMonths: (Int) -> Unit,
    onOpenMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    tags: TagsState? = null,
    tagBreakdown: TagBreakdownState? = null,
    tagActions: TagActions = TagActions()
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        ScreenTitle(stringResource(UiR.string.nav_insights))
        donut?.let { DonutCard(it, onSelectPeriod, onStepPeriod, onSelectPast, onOpenCategory) }
        tags?.let { TagCard(it, tagActions) }
        heatmap?.let { HeatmapCard(it, onOpenDay) }
        monthly?.let { MonthlyCard(it, onSelectMonths, onOpenMonth) }
        Spacer(Modifier.height(8.dp))
    }
    tagBreakdown?.let { TagBreakdownSheet(it, tagActions) }
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
