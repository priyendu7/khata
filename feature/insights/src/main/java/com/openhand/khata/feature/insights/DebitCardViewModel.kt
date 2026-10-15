package com.openhand.khata.feature.insights

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.AccountRepository
import com.openhand.khata.core.data.TransactionRepository
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.CategoryBreakdown
import com.openhand.khata.core.model.TransactionFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Instant
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A debit card's page (#155): its bank account, the bank accounts it can be linked to, and its
 * spending by category over a period, with the same chips and arrows as Insights (#98).
 */
data class DebitCardState(
    val card: Account,
    val linkedAccount: Account?,
    val bankAccounts: List<Account>,
    override val period: ChartPeriod,
    override val span: DateSpan,
    override val from: Long,
    override val until: Long,
    val spending: CategoryBreakdown,
    override val back: Int = 0,
    override val choices: List<DateSpan> = emptyList()
) : ShownPeriod

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DebitCardViewModel internal constructor(
    private val cardId: Long,
    private val accounts: AccountRepository,
    transactions: TransactionRepository,
    today: Flow<LocalDate>,
    private val zone: ZoneId,
    private val firstDayOfWeek: DayOfWeek
) : ViewModel() {
    @Inject constructor(
        savedState: SavedStateHandle,
        accounts: AccountRepository,
        transactions: TransactionRepository
    ) : this(
        checkNotNull(savedState.get<Long>(CARD_ID_ARG)),
        accounts,
        transactions,
        currentDate(),
        ZoneId.systemDefault(),
        WeekFields.of(Locale.getDefault()).firstDayOfWeek
    )

    private val selection = MutableStateFlow(Selection(ChartPeriod.MONTH))

    private val firstYear: Flow<Int?> = transactions.observeFirstTimestamp()
        .map { it?.let { millis -> Instant.ofEpochMilli(millis).atZone(zone).year } }
        .distinctUntilChanged()

    /** Null until the first load, and once the card is gone or isn't a debit card. */
    val state: StateFlow<DebitCardState?> = combine(
        selection,
        today.distinctUntilChanged(),
        firstYear,
        accounts.observeAccounts()
    ) { selected, date, first, all -> Inputs(selected, date, first, all) }
        .flatMapLatest { inputs ->
            val card = inputs.accounts.firstOrNull {
                it.id == cardId && it.type == AccountType.DEBIT_CARD
            } ?: return@flatMapLatest flowOf(null)
            val selected = inputs.selection
            val span = selected.custom
                ?: DateSpan.of(selected.period, inputs.today, firstDayOfWeek, selected.back)
            val from = span.from(zone)
            val until = span.until(zone)
            transactions.observeCategorySpendingForAccount(from, until, card).map { spent ->
                DebitCardState(
                    card = card,
                    linkedAccount = inputs.accounts.firstOrNull { it.id == card.linkedAccountId },
                    bankAccounts = inputs.accounts.filter { it.type == AccountType.BANK },
                    period = selected.period,
                    span = span,
                    from = from,
                    until = until,
                    spending = CategoryBreakdown.of(spent),
                    back = selected.back,
                    choices = choices(selected.period, inputs.today, inputs.firstYear)
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Links the card to bank account [accountId], or unlinks it with null. */
    fun link(accountId: Long?) {
        viewModelScope.launch { accounts.setLinkedAccount(cardId, accountId) }
    }

    /** This week, month or year; for a custom period use [selectRange]. */
    fun selectPeriod(period: ChartPeriod) {
        require(period != ChartPeriod.CUSTOM) { "Use selectRange for custom dates" }
        selection.value = Selection(period)
    }

    /** One period back (-1) or forward (+1), never past the current one or for custom dates. */
    fun stepPeriod(delta: Int) {
        selection.update {
            if (it.period == ChartPeriod.CUSTOM) it else it.copy(back = maxOf(0, it.back - delta))
        }
    }

    /** The period [back] periods before the current one, of the kind already shown. */
    fun selectPast(back: Int) {
        require(back >= 0) { "Only past periods" }
        selection.update { if (it.period == ChartPeriod.CUSTOM) it else it.copy(back = back) }
    }

    /** Days from [first] to [last], both included. */
    fun selectRange(first: LocalDate, last: LocalDate) {
        selection.value = Selection(ChartPeriod.CUSTOM, DateSpan(first, last))
    }

    /** The transactions list for the shown period's spending, of one category or any (null). */
    fun filter(categoryId: Long?): TransactionFilter? {
        val shown = state.value ?: return null
        return TransactionFilter(
            categoryIds = setOfNotNull(categoryId),
            accountId = cardId,
            from = shown.from,
            until = shown.until
        )
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

    /** A preset period [back] periods before the current one, or custom dates. */
    private data class Selection(
        val period: ChartPeriod,
        val custom: DateSpan? = null,
        val back: Int = 0
    )

    private data class Inputs(
        val selection: Selection,
        val today: LocalDate,
        val firstYear: Int?,
        val accounts: List<Account>
    )

    private companion object {
        const val MONTH_CHOICES = 24
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
