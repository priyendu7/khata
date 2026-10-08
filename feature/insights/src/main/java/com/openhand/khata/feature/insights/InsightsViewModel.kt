package com.openhand.khata.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.CategoryRepository
import com.openhand.khata.core.data.TransactionRepository
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountSpend
import com.openhand.khata.core.model.AmountEntry
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.CategoryBreakdown
import com.openhand.khata.core.model.HeatLevels
import com.openhand.khata.core.model.MonthlyComparison
import com.openhand.khata.core.model.Tag
import com.openhand.khata.core.model.TagSpend
import com.openhand.khata.core.model.TransferSummary
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

/** Spending by account for one period: each account, biggest first, No account among them. */
data class AccountsState(
    override val period: ChartPeriod,
    override val span: DateSpan,
    override val from: Long,
    override val until: Long,
    val spending: List<AccountSpend>,
    override val back: Int = 0,
    override val choices: List<DateSpan> = emptyList()
) : ShownPeriod

/** What moved between the user's accounts in one period (#113); never spending or income. */
data class TransfersState(
    override val period: ChartPeriod,
    override val span: DateSpan,
    override val from: Long,
    override val until: Long,
    val summary: TransferSummary,
    override val back: Int = 0,
    override val choices: List<DateSpan> = emptyList()
) : ShownPeriod

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

/** A card's shown period, with everything [ShownPeriod] needs. */
private data class CardPeriod(
    val period: ChartPeriod,
    val span: DateSpan,
    val from: Long,
    val until: Long,
    val back: Int,
    val choices: List<DateSpan>
)

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
    private val openBreakdown = MutableStateFlow<BreakdownOf?>(null)
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

    /** Each card's selection, shared by the card and the breakdown sheet that follows it. */
    private val shownCards = PeriodCard.entries.associateWith { card ->
        shown(card).shareIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            replay = 1
        )
    }

    /**
     * [card]'s state, built from its [CardPeriod] and what [load] gives for it; updated live, and
     * null until the first load.
     */
    private fun <T, S> cardState(
        card: PeriodCard,
        load: (from: Long, until: Long) -> Flow<T>,
        build: (CardPeriod, T) -> S
    ): StateFlow<S?> = shownCards.getValue(card)
        .flatMapLatest { shown ->
            val from = shown.span.from(zone)
            val until = shown.span.until(zone)
            combine(load(from, until), firstYear) { loaded, first ->
                val period = shown.selection.period
                build(
                    CardPeriod(
                        period,
                        shown.span,
                        from,
                        until,
                        shown.selection.back,
                        choices(period, shown.today, first)
                    ),
                    loaded
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** The category donut. */
    val donut: StateFlow<DonutState?> =
        cardState(PeriodCard.CATEGORIES, transactions::observeCategorySpending) { p, spent ->
            DonutState(
                p.period,
                p.span,
                p.from,
                p.until,
                CategoryBreakdown.of(spent),
                p.back,
                p.choices
            )
        }

    /** The tag card, with its own period. */
    val tags: StateFlow<TagsState?> =
        cardState(PeriodCard.TAGS, transactions::observeTagSpending) { p, spent ->
            TagsState(p.period, p.span, p.from, p.until, spent, p.back, p.choices)
        }

    /** The account card, with its own period. */
    val accounts: StateFlow<AccountsState?> =
        cardState(PeriodCard.ACCOUNTS, transactions::observeAccountSpending) { p, spent ->
            AccountsState(p.period, p.span, p.from, p.until, spent, p.back, p.choices)
        }

    /** The Transfers card, with its own period. */
    val transfers: StateFlow<TransfersState?> =
        cardState(PeriodCard.TRANSFERS, transactions::observeTransfers) { p, summary ->
            TransfersState(p.period, p.span, p.from, p.until, summary, p.back, p.choices)
        }

    /**
     * The open tag's or account's breakdown, following its card's period; null when none is
     * open.
     */
    val breakdown: StateFlow<BreakdownState?> = openBreakdown
        .flatMapLatest { open ->
            if (open == null) return@flatMapLatest flowOf(null)
            shownCards.getValue(open.card).flatMapLatest { shown ->
                val from = shown.span.from(zone)
                val until = shown.span.until(zone)
                val spending = when (open) {
                    is BreakdownOf.OfTag ->
                        transactions.observeCategorySpendingForTag(from, until, open.tag)
                    is BreakdownOf.OfAccount ->
                        transactions.observeCategorySpendingForAccount(from, until, open.account)
                }
                spending.map {
                    BreakdownState(
                        of = open,
                        period = shown.selection.period,
                        span = shown.span,
                        from = from,
                        until = until,
                        breakdown = CategoryBreakdown.of(it)
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Opens [tag]'s breakdown, or Untagged's for null. */
    fun openTag(tag: Tag?) {
        openBreakdown.value = BreakdownOf.OfTag(tag)
    }

    /** Opens [account]'s breakdown, or No account's for null. */
    fun openAccount(account: Account?) {
        openBreakdown.value = BreakdownOf.OfAccount(account)
    }

    fun closeBreakdown() {
        openBreakdown.value = null
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
