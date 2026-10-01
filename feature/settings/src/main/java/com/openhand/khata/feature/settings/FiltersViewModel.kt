package com.openhand.khata.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.openhand.khata.sms.ingest.SmsImportSettings
import com.openhand.khata.sms.parser.SmsFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Shows this switch first, from a filter named in Test a message; absent for none. */
const val FILTERS_SHOW_ARG = "filter"

@HiltViewModel
class FiltersViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val settings: SmsImportSettings
) : ViewModel() {
    val filters: StateFlow<SmsFilters> = settings.filters

    val shown: FilterSwitch? = savedState.get<String>(FILTERS_SHOW_ARG)
        ?.let { name -> FilterSwitch.entries.firstOrNull { it.name == name } }

    /** Applies to the next SMS: the ingestor rebuilds its parser when a filter changes. */
    fun set(switch: FilterSwitch, on: Boolean) =
        settings.setFilters(switch.set(settings.filters.value, on))
}
