package com.openhand.khata.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.TransactionRepository
import com.openhand.khata.core.model.CategoryBreakdown
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
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

/** A preset period, or [ChartPeriod.CUSTOM] with its dates. */
private data class Selection(val period: ChartPeriod, val custom: DateSpan? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel internal constructor(
    transactions: TransactionRepository,
    today: Flow<LocalDate>,
    zone: ZoneId,
    firstDayOfWeek: DayOfWeek
) : ViewModel() {
    @Inject constructor(transactions: TransactionRepository) : this(
        transactions,
        currentDate(),
        ZoneId.systemDefault(),
        WeekFields.of(Locale.getDefault()).firstDayOfWeek
    )

    private val selection = MutableStateFlow(Selection(ChartPeriod.MONTH))

    /** Null until the first load; then updated live as transactions or the period change. */
    val donut: StateFlow<DonutState?> =
        combine(selection, today.distinctUntilChanged()) { selected, date ->
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

    /** This week, month or year; for a custom period use [selectRange]. */
    fun selectPeriod(period: ChartPeriod) {
        require(period != ChartPeriod.CUSTOM) { "Use selectRange for custom dates" }
        selection.value = Selection(period)
    }

    /** Days from [first] to [last], both included. */
    fun selectRange(first: LocalDate, last: LocalDate) {
        selection.value = Selection(ChartPeriod.CUSTOM, DateSpan(first, last))
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
