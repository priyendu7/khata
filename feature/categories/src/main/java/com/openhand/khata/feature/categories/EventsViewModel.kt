package com.openhand.khata.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.EventRepository
import com.openhand.khata.core.data.SaveEventResult
import com.openhand.khata.core.model.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class EventsViewModel @Inject constructor(private val events: EventRepository) : ViewModel() {
    /** Newest first; null while loading. */
    val all: StateFlow<List<Event>?> = events.observeEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** [onResult] runs on the main thread; a taken name comes back so the dialog can say so. */
    fun save(event: Event, onResult: (SaveEventResult) -> Unit) {
        viewModelScope.launch { onResult(events.save(event, ZoneId.systemDefault())) }
    }

    fun delete(event: Event) {
        viewModelScope.launch { events.delete(event.id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
