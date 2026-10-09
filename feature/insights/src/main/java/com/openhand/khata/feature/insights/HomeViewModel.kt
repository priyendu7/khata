package com.openhand.khata.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.BackupReminderRepository
import com.openhand.khata.core.data.ReviewRepository
import com.openhand.khata.core.data.TransactionRepository
import com.openhand.khata.core.model.CategoryBreakdown
import com.openhand.khata.core.model.CategorySpend
import com.openhand.khata.core.model.Totals
import com.openhand.khata.core.model.TransactionFilter
import com.openhand.khata.core.model.TransactionListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Home's figures (PRD feature 5, #130), with the same rules as Insights: transfers never count,
 * refunds reduce spending, and each transaction counts in its month (#93).
 */
data class HomeSummary(
    val month: Totals,
    /** Against last month by the same day; null when nothing was spent by then. */
    val change: MonthChange?,
    /** This month's donut. */
    val categories: CategoryBreakdown,
    /** The newest transactions, newest first. */
    val recent: List<TransactionListItem>,
    val hasTransactions: Boolean
) {
    /** The biggest categories this month; empty when nothing was spent. */
    val topCategories: List<CategorySpend>
        get() = categories.slices.take(TOP_CATEGORIES)

    companion object {
        const val TOP_CATEGORIES = 3
        const val RECENT = 5
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel internal constructor(
    transactions: TransactionRepository,
    reminders: BackupReminderRepository,
    review: ReviewRepository,
    today: Flow<LocalDate>,
    zone: ZoneId
) : ViewModel() {
    @Inject constructor(
        transactions: TransactionRepository,
        reminders: BackupReminderRepository,
        review: ReviewRepository
    ) : this(transactions, reminders, review, currentDate(), ZoneId.systemDefault())

    /** Null until the first load; then updated live as transactions change. */
    val summary: StateFlow<HomeSummary?> = today
        .distinctUntilChanged()
        .flatMapLatest { date ->
            val periods = SummaryPeriods.of(date, zone)
            combine(
                transactions.observeTotals(periods.monthFrom, periods.monthUntil),
                transactions.observeTotals(periods.lastMonthFrom, periods.lastMonthUntil),
                transactions.observeCategorySpending(periods.monthFrom, periods.monthUntil),
                transactions.observe(TransactionFilter(), zone, limit = HomeSummary.RECENT),
                transactions.observeAny()
            ) { month, lastMonth, spending, recent, any ->
                HomeSummary(
                    month = month,
                    change = MonthChange.of(month.spentPaise, lastMonth.spentPaise),
                    categories = CategoryBreakdown.of(spending),
                    recent = recent,
                    hasTransactions = any
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** The reminder interval in days while a backup is due (PRD feature 6), else null. */
    val backupDueDays: StateFlow<Int?> = reminders.observeDue()
        .combine(reminders.settings) { due, settings -> settings.interval.days.takeIf { due } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Transactions from new payees waiting in To review (PRD feature 4). */
    val reviewCount: StateFlow<Int> = review.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
