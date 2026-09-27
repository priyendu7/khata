package com.openhand.khata.feature.payees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.CategoryRepository
import com.openhand.khata.core.data.PayeeRepository
import com.openhand.khata.core.data.TagRepository
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.Payee
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class PayeesViewModel @Inject constructor(
    private val payees: PayeeRepository,
    categories: CategoryRepository,
    private val tags: TagRepository
) : ViewModel() {
    /** Null until loaded. */
    val all: StateFlow<List<Payee>?> = payees.observePayees()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Archived ones included, so a payee whose default was archived still shows its name. */
    val categories: StateFlow<List<Category>> = categories.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val tagQuery = MutableStateFlow("")

    val tagSuggestions: StateFlow<List<String>> = tagQuery.debounce(TAG_DEBOUNCE_MILLIS)
        .mapLatest { tags.suggestions(it) }
        .map { found -> found.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun onTagQueryChange(query: String) {
        tagQuery.value = query
    }

    fun save(payee: Payee) {
        viewModelScope.launch { payees.save(payee) }
    }

    fun merge(from: Payee, into: Payee) {
        viewModelScope.launch { payees.merge(from.id, into.id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TAG_DEBOUNCE_MILLIS = 150L
    }
}
