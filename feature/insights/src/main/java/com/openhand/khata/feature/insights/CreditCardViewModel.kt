package com.openhand.khata.feature.insights

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.AccountRepository
import com.openhand.khata.core.data.CardRepository
import com.openhand.khata.core.data.CardSummary
import com.openhand.khata.core.model.CardCycle
import com.openhand.khata.core.model.CategoryBreakdown
import com.openhand.khata.core.model.StatementSummary
import com.openhand.khata.core.model.TransactionFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The card's id in a card page's route (#155). */
const val CARD_ID_ARG = "cardId"

/**
 * A credit card's page (#155): its bills, and the spending of the cycle being looked at, which is
 * [shownStatement]'s or, when that's null, the one no statement has billed yet.
 */
data class CreditCardState(
    val summary: CardSummary,
    val shownStatement: StatementSummary?,
    val spending: CategoryBreakdown,
    val today: LocalDate
) {
    val shownCycle: CardCycle get() = shownStatement?.cycle ?: summary.currentCycle
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CreditCardViewModel internal constructor(
    private val cardId: Long,
    private val cards: CardRepository,
    private val accounts: AccountRepository,
    today: Flow<LocalDate>,
    private val zone: ZoneId
) : ViewModel() {
    @Inject constructor(
        savedState: SavedStateHandle,
        cards: CardRepository,
        accounts: AccountRepository
    ) : this(
        checkNotNull(savedState.get<Long>(CARD_ID_ARG)),
        cards,
        accounts,
        currentDate(),
        ZoneId.systemDefault()
    )

    /** The statement whose cycle is shown, by id; null for the cycle running now. */
    private val shownId = MutableStateFlow<Long?>(null)

    /** Null until the first load, and once the card is gone. */
    val state: StateFlow<CreditCardState?> = today
        .distinctUntilChanged()
        .flatMapLatest { date ->
            combine(cards.observeCard(cardId, date, zone), shownId) { summary, id ->
                summary?.let { Shown(it, it.statements.firstOrNull { s -> s.statement.id == id }) }
            }.flatMapLatest { shown ->
                if (shown == null) return@flatMapLatest flowOf(null)
                val cycle = shown.statement?.cycle ?: shown.summary.currentCycle
                cards.observeCycleSpending(shown.summary.card, cycle, zone).map {
                    CreditCardState(
                        shown.summary,
                        shown.statement,
                        CategoryBreakdown.of(it),
                        date
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Shows statement [id]'s cycle, or the one running now for null. */
    fun showStatement(id: Long?) {
        shownId.value = id
    }

    /** Sets the card's billing day (#154), 1–31, or clears it with null. */
    fun setBillingDay(day: Int?) {
        viewModelScope.launch { accounts.setBillingDay(cardId, day) }
    }

    /** The transactions list for the shown cycle's spending, of one category or any (null). */
    fun filter(categoryId: Long?): TransactionFilter? {
        val cycle = state.value?.shownCycle ?: return null
        return TransactionFilter(
            categoryIds = setOfNotNull(categoryId),
            accountId = cardId,
            from = cycle.first.startMillis(zone),
            until = cycle.last.plusDays(1).startMillis(zone)
        )
    }

    private data class Shown(val summary: CardSummary, val statement: StatementSummary?)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
