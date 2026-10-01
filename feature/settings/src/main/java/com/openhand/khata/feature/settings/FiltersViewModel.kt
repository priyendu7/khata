package com.openhand.khata.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.IgnoreRuleRepository
import com.openhand.khata.core.model.SmsIgnoreRule
import com.openhand.khata.sms.ingest.SmsImportSettings
import com.openhand.khata.sms.parser.SmsFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Shows this switch first, from a filter named in Test a message; absent for none. */
const val FILTERS_SHOW_ARG = "filter"

@HiltViewModel
class FiltersViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val settings: SmsImportSettings,
    private val ignoreRules: IgnoreRuleRepository
) : ViewModel() {
    val filters: StateFlow<SmsFilters> = settings.filters

    /** Made from To review with "ignore this sender" or "ignore messages like this". */
    val rules: StateFlow<List<SmsIgnoreRule>> = ignoreRules.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), emptyList())

    val shown: FilterSwitch? = savedState.get<String>(FILTERS_SHOW_ARG)
        ?.let { name -> FilterSwitch.entries.firstOrNull { it.name == name } }

    /** Applies to the next SMS: the ingestor rebuilds its parser when a filter changes. */
    fun set(switch: FilterSwitch, on: Boolean) =
        settings.setFilters(switch.set(settings.filters.value, on))

    fun setRuleEnabled(rule: SmsIgnoreRule, on: Boolean) {
        viewModelScope.launch { ignoreRules.setEnabled(rule.id, on) }
    }

    fun deleteRule(rule: SmsIgnoreRule) {
        viewModelScope.launch { ignoreRules.delete(rule.id) }
    }

    private companion object {
        const val STOP_AFTER_MS = 5_000L
    }
}
