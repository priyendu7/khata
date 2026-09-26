package com.openhand.khata.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.AccountRepository
import com.openhand.khata.core.data.CategoryRepository
import com.openhand.khata.core.data.TagRepository
import com.openhand.khata.core.data.TransactionRepository
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.Tag
import com.openhand.khata.core.model.TransactionFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** What the list shows: the filter, and the days that match it (null until the first load). */
data class ListState(val filter: TransactionFilter, val days: List<DaySection>?)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    categories: CategoryRepository,
    tags: TagRepository,
    accounts: AccountRepository
) : ViewModel() {
    private val _filter = MutableStateFlow(TransactionFilter())
    val filter: StateFlow<TransactionFilter> = _filter.asStateFlow()

    /** The matching transactions grouped by day; typing in search waits for a short pause. */
    val days: StateFlow<ListState?> = _filter
        .debounce { if (it.query.isNotEmpty()) SEARCH_DEBOUNCE_MILLIS else 0L }
        .distinctUntilChanged()
        .flatMapLatest { filter ->
            transactions.observe(filter).map {
                ListState(filter, groupByDay(it, ZoneId.systemDefault()))
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Every category, archived ones too: old transactions can still be filtered by them. */
    val categories: StateFlow<List<Category>> = categories.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val tags: StateFlow<List<Tag>> = tags.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val accounts: StateFlow<List<Account>> = accounts.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun setQuery(query: String) = _filter.update { it.copy(query = query) }

    fun setCategory(id: Long?) = _filter.update { it.copy(categoryId = id) }

    fun setTag(id: Long?) = _filter.update { it.copy(tagId = id) }

    fun setAccount(id: Long?) = _filter.update { it.copy(accountId = id) }

    /** Days from [first] to [last], both included, in the phone's time zone; nulls clear it. */
    fun setDates(first: LocalDate?, last: LocalDate?) {
        val zone = ZoneId.systemDefault()
        _filter.update {
            it.copy(
                from = first?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
                until = last?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
            )
        }
    }

    /** Clears the filters but keeps the search text. */
    fun clearFilters() = _filter.update { TransactionFilter(query = it.query) }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SEARCH_DEBOUNCE_MILLIS = 200L
    }
}
