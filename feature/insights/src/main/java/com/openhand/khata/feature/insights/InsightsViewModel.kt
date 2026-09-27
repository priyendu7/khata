package com.openhand.khata.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.CategoryRepository
import com.openhand.khata.core.data.TransactionRepository
import com.openhand.khata.core.model.AmountEntry
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.CategoryBreakdown
import com.openhand.khata.core.model.HeatLevels
import com.openhand.khata.core.model.MonthlyComparison
import com.openhand.khata.core.model.spendingByDay
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

/** The category donut for one period (PRD feature 5). */
data class DonutState(
    val period: ChartPeriod,
    val span: DateSpan,
    /** [span] as the `[from, until)` milliseconds the transactions list filters by. */
    val from: Long,
    val until: Long,
    val breakdown: CategoryBreakdown
)

/**
 * The calendar heatmap (PRD feature 5): spending on each day from [first] to [today], and the
 * color levels for it. Days with nothing spent aren't in [days].
 */
data class HeatmapState(
    val first: LocalDate,
    val today: LocalDate,
    val firstDayOfWeek: DayOfWeek,
    val days: Map<LocalDate, Long>,
    val levels: HeatLevels
)

/** The monthly comparison over the last [months] months, this one included. */
data class MonthlyState(val months: Int, val comparison: MonthlyComparison)

/** Everything both the heatmap and the monthly chart are worked out from. */
private data class ChartData(
    val today: LocalDate,
    val amounts: List<AmountEntry>,
    val categories: List<Category>
)

/** A preset period, or [ChartPeriod.CUSTOM] with its dates. */
private data class Selection(val period: ChartPeriod, val custom: DateSpan? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel internal constructor(
    transactions: TransactionRepository,
    categories: CategoryRepository,
    today: Flow<LocalDate>,
    private val zone: ZoneId,
    firstDayOfWeek: DayOfWeek
) : ViewModel() {
    @Inject constructor(transactions: TransactionRepository, categories: CategoryRepository) : this(
        transactions,
        categories,
        currentDate(),
        ZoneId.systemDefault(),
        WeekFields.of(Locale.getDefault()).firstDayOfWeek
    )

    private val selection = MutableStateFlow(Selection(ChartPeriod.MONTH))
    private val monthCount = MutableStateFlow(SHORT_MONTHS)
    private val days = today.distinctUntilChanged()

    /** Null until the first load; then updated live as transactions or the period change. */
    val donut: StateFlow<DonutState?> =
        combine(selection, days) { selected, date ->
            selected.period to
                (selected.custom ?: DateSpan.of(selected.period, date, firstDayOfWeek))
        }
            .distinctUntilChanged()
            .flatMapLatest { (period, span) ->
                val from = span.from(zone)
                val until = span.until(zone)
                transactions.observeCategorySpending(from, until).map {
                    DonutState(period, span, from, until, CategoryBreakdown.of(it))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** One query covers both charts: the heatmap's 12 months and the monthly chart's. */
    private val chartData: Flow<ChartData> = days
        .flatMapLatest { date ->
            val firstMonth = YearMonth.from(date).minusMonths(LONG_MONTHS - 1L).atDay(1)
            val from = minOf(heatmapStart(date), firstMonth)
            val until = YearMonth.from(date).plusMonths(1).atDay(1)
            combine(
                transactions.observeAmounts(from.startMillis(zone), until.startMillis(zone)),
                categories.observeAll()
            ) { amounts, all -> ChartData(date, amounts, all) }
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    /** Null until the first load. */
    val heatmap: StateFlow<HeatmapState?> = chartData
        .map { data ->
            val days = spendingByDay(data.amounts, zone)
                .filterKeys { it in heatmapStart(data.today)..data.today }
            HeatmapState(
                first = heatmapStart(data.today),
                today = data.today,
                firstDayOfWeek = firstDayOfWeek,
                days = days,
                levels = HeatLevels.of(days.values)
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Null until the first load. */
    val monthly: StateFlow<MonthlyState?> = combine(chartData, monthCount) { data, count ->
        val thisMonth = YearMonth.from(data.today)
        val months = (count - 1 downTo 0).map { thisMonth.minusMonths(it.toLong()) }
        MonthlyState(count, MonthlyComparison.of(data.amounts, months, zone, data.categories))
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Six or twelve months on the monthly chart. */
    fun selectMonths(count: Int) {
        require(count == SHORT_MONTHS || count == LONG_MONTHS) { "6 or 12 months" }
        monthCount.value = count
    }

    /** [date] as the `[from, until)` milliseconds the transactions list filters by. */
    fun rangeOf(date: LocalDate): Pair<Long, Long> = rangeOf(DateSpan(date, date))

    fun rangeOf(month: YearMonth): Pair<Long, Long> =
        rangeOf(DateSpan(month.atDay(1), month.atEndOfMonth()))

    private fun rangeOf(span: DateSpan) = span.from(zone) to span.until(zone)

    /** This week, month or year; for a custom period use [selectRange]. */
    fun selectPeriod(period: ChartPeriod) {
        require(period != ChartPeriod.CUSTOM) { "Use selectRange for custom dates" }
        selection.value = Selection(period)
    }

    /** Days from [first] to [last], both included. */
    fun selectRange(first: LocalDate, last: LocalDate) {
        selection.value = Selection(ChartPeriod.CUSTOM, DateSpan(first, last))
    }

    companion object {
        const val SHORT_MONTHS = 6
        const val LONG_MONTHS = 12
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** The heatmap covers the last 12 months: a year back from today, today included. */
        internal fun heatmapStart(today: LocalDate): LocalDate = today.minusYears(1).plusDays(1)
    }
}
