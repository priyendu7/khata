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
import com.openhand.khata.core.model.Tag
import com.openhand.khata.core.model.TagSpend
import com.openhand.khata.core.model.TransactionFilter
import com.openhand.khata.core.model.spendingByDay
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Instant
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** The category donut for one period (PRD feature 5). */
data class DonutState(
    override val period: ChartPeriod,
    override val span: DateSpan,
    override val from: Long,
    override val until: Long,
    val breakdown: CategoryBreakdown,
    override val back: Int = 0,
    override val choices: List<DateSpan> = emptyList()
) : ShownPeriod

/** Spending by tag for one period: each tag, biggest first, then Untagged. */
data class TagsState(
    override val period: ChartPeriod,
    override val span: DateSpan,
    override val from: Long,
    override val until: Long,
    val spending: List<TagSpend>,
    override val back: Int = 0,
    override val choices: List<DateSpan> = emptyList()
) : ShownPeriod

/** One tag's spending (or Untagged's, when [tag] is null) by category, for the tag card's period. */
data class TagBreakdownState(
    val tag: Tag?,
    val period: ChartPeriod,
    val span: DateSpan,
    val from: Long,
    val until: Long,
    val breakdown: CategoryBreakdown
) {
    /** The transactions list for this tag and period, of one category or of any (null). */
    fun filter(categoryId: Long? = null) = TransactionFilter(
        categoryId = categoryId,
        tagId = tag?.id,
        untagged = tag == null,
        from = from,
        until = until
    )
}

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

/**
 * A preset period [back] periods before the current one, or [ChartPeriod.CUSTOM] with its dates.
 * [back] is kept rather than dates so that "last month" moves on when the month does.
 */
private data class Selection(
    val period: ChartPeriod,
    val custom: DateSpan? = null,
    val back: Int = 0
)

/** What a card shows: the [selection] worked out against [today]. */
private data class Shown(val selection: Selection, val today: LocalDate, val span: DateSpan)

/** The tag whose breakdown is open; a null [tag] is Untagged. */
private data class OpenTag(val tag: Tag?)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel internal constructor(
    transactions: TransactionRepository,
    categories: CategoryRepository,
    today: Flow<LocalDate>,
    private val zone: ZoneId,
    private val firstDayOfWeek: DayOfWeek
) : ViewModel() {
    @Inject constructor(transactions: TransactionRepository, categories: CategoryRepository) : this(
        transactions,
        categories,
        currentDate(),
        ZoneId.systemDefault(),
        WeekFields.of(Locale.getDefault()).firstDayOfWeek
    )

    private val selections = PeriodCard.entries.associateWith {
        MutableStateFlow(Selection(ChartPeriod.MONTH))
    }
    private val openTag = MutableStateFlow<OpenTag?>(null)
    private val monthCount = MutableStateFlow(SHORT_MONTHS)
    private val days = today.distinctUntilChanged()

    /** The year of the first transaction: the year list starts there. Null with none. */
    private val firstYear: Flow<Int?> = transactions.observeFirstTimestamp()
        .map { it?.let { millis -> Instant.ofEpochMilli(millis).atZone(zone).year } }
        .distinctUntilChanged()

    /** [card]'s selection worked out against today, as today moves on. */
    private fun shown(card: PeriodCard): Flow<Shown> =
        combine(selections.getValue(card), days) { selected, date ->
            val span = selected.custom
                ?: DateSpan.of(selected.period, date, firstDayOfWeek, selected.back)
            Shown(selected, date, span)
        }.distinctUntilChanged()

    /** Null until the first load; then updated live as transactions or the period change. */
    val donut: StateFlow<DonutState?> = shown(PeriodCard.CATEGORIES)
        .flatMapLatest { shown ->
            val span = shown.span
            val from = span.from(zone)
            val until = span.until(zone)
            combine(transactions.observeCategorySpending(from, until), firstYear) { spent, first ->
                DonutState(
                    period = shown.selection.period,
                    span = span,
                    from = from,
                    until = until,
                    breakdown = CategoryBreakdown.of(spent),
                    back = shown.selection.back,
                    choices = choices(shown.selection.period, shown.today, first)
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val shownTags = shown(PeriodCard.TAGS)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    /** The tag card, with its own period; null until the first load. */
    val tags: StateFlow<TagsState?> = shownTags
        .flatMapLatest { shown ->
            val span = shown.span
            val from = span.from(zone)
            val until = span.until(zone)
            combine(transactions.observeTagSpending(from, until), firstYear) { spent, first ->
                TagsState(
                    period = shown.selection.period,
                    span = span,
                    from = from,
                    until = until,
                    spending = spent,
                    back = shown.selection.back,
                    choices = choices(shown.selection.period, shown.today, first)
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** The open tag's breakdown, following the tag card's period; null when none is open. */
    val tagBreakdown: StateFlow<TagBreakdownState?> = combine(openTag, shownTags, ::Pair)
        .flatMapLatest { (open, shown) ->
            if (open == null) return@flatMapLatest flowOf(null)
            val from = shown.span.from(zone)
            val until = shown.span.until(zone)
            transactions.observeCategorySpendingForTag(from, until, open.tag).map {
                TagBreakdownState(
                    tag = open.tag,
                    period = shown.selection.period,
                    span = shown.span,
                    from = from,
                    until = until,
                    breakdown = CategoryBreakdown.of(it)
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Opens [tag]'s breakdown, or Untagged's for null. */
    fun openTag(tag: Tag?) {
        openTag.value = OpenTag(tag)
    }

    fun closeTag() {
        openTag.value = null
    }

    /** The last 24 months, or every year since the first transaction; weeks have no list. */
    private fun choices(period: ChartPeriod, today: LocalDate, firstYear: Int?): List<DateSpan> {
        val count = when (period) {
            ChartPeriod.MONTH -> MONTH_CHOICES
            ChartPeriod.YEAR -> today.year - minOf(firstYear ?: today.year, today.year) + 1
            ChartPeriod.WEEK, ChartPeriod.CUSTOM -> 0
        }
        return (0 until count).map { DateSpan.of(period, today, firstDayOfWeek, it) }
    }

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

    /** This week, month or year on [card]; for a custom period use [selectRange]. */
    fun selectPeriod(period: ChartPeriod, card: PeriodCard = PeriodCard.CATEGORIES) {
        require(period != ChartPeriod.CUSTOM) { "Use selectRange for custom dates" }
        selections.getValue(card).value = Selection(period)
    }

    /** One period back (-1) or forward (+1), never past the current one or for custom dates. */
    fun stepPeriod(delta: Int, card: PeriodCard = PeriodCard.CATEGORIES) {
        selections.getValue(card).update {
            if (it.period == ChartPeriod.CUSTOM) it else it.copy(back = maxOf(0, it.back - delta))
        }
    }

    /** The period [back] periods before the current one, of the kind already shown. */
    fun selectPast(back: Int, card: PeriodCard = PeriodCard.CATEGORIES) {
        require(back >= 0) { "Only past periods" }
        selections.getValue(card).update {
            if (it.period == ChartPeriod.CUSTOM) it else it.copy(back = back)
        }
    }

    /** Days from [first] to [last], both included. */
    fun selectRange(first: LocalDate, last: LocalDate, card: PeriodCard = PeriodCard.CATEGORIES) {
        selections.getValue(card).value = Selection(ChartPeriod.CUSTOM, DateSpan(first, last))
    }

    companion object {
        const val SHORT_MONTHS = 6
        const val LONG_MONTHS = 12
        private const val MONTH_CHOICES = 24
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** The heatmap covers the last 12 months: a year back from today, today included. */
        internal fun heatmapStart(today: LocalDate): LocalDate = today.minusYears(1).plusDays(1)
    }
}
