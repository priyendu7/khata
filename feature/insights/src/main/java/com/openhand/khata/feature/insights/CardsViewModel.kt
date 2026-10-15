package com.openhand.khata.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.CardRepository
import com.openhand.khata.core.data.CardSummary
import com.openhand.khata.core.data.DebitCardSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
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
 * The Cards tab (#155): each credit card with its bill, and each debit card with what it spent
 * this month. [today] decides which bills are due soon or overdue.
 */
data class CardsState(
    val credit: List<CardSummary>,
    val debit: List<DebitCardSummary>,
    val today: LocalDate
) {
    val isEmpty: Boolean get() = credit.isEmpty() && debit.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CardsViewModel internal constructor(
    cards: CardRepository,
    today: Flow<LocalDate>,
    zone: ZoneId
) : ViewModel() {
    @Inject constructor(cards: CardRepository) : this(cards, currentDate(), ZoneId.systemDefault())

    /** Null until the first load. */
    val state: StateFlow<CardsState?> = today
        .distinctUntilChanged()
        .flatMapLatest { date ->
            val month = YearMonth.from(date)
            combine(
                cards.observeCards(date, zone),
                cards.observeDebitCards(
                    month.atDay(1).startMillis(zone),
                    month.plusMonths(1).atDay(1).startMillis(zone)
                )
            ) { credit, debit -> CardsState(credit, debit, date) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
