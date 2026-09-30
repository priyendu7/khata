package com.openhand.khata.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.BackupReminderRepository
import com.openhand.khata.core.data.ReviewRepository
import com.openhand.khata.core.data.TransactionRepository
import com.openhand.khata.core.model.CategorySpend
import com.openhand.khata.core.model.Totals
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** Home's figures (PRD feature 5). Transfers never count; refunds reduce spending. */
data class HomeSummary(
    val month: Totals,
    val spentTodayPaise: Long,
    /** Null when nothing was spent this month. */
    val topCategory: CategorySpend?,
    val hasTransactions: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    transactions: TransactionRepository,
    reminders: BackupReminderRepository,
    review: ReviewRepository
) : ViewModel() {
    /** Null until the first load; then updated live as transactions change. */
    val summary: StateFlow<HomeSummary?> = currentDate()
        .distinctUntilChanged()
        .flatMapLatest { today ->
            val periods = SummaryPeriods.of(today, ZoneId.systemDefault())
            combine(
                transactions.observeTotals(periods.monthFrom, periods.monthUntil),
                transactions.observeTotals(periods.todayFrom, periods.todayUntil),
                transactions.observeTopCategory(periods.monthFrom, periods.monthUntil),
                transactions.observeAny()
            ) { month, day, top, any -> HomeSummary(month, day.spentPaise, top, any) }
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
