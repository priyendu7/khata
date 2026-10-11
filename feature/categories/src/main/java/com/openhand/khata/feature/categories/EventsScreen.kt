package com.openhand.khata.feature.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.data.SaveEventResult
import com.openhand.khata.core.model.Event
import com.openhand.khata.core.ui.EmptyState
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.SegmentListItem
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.SubScreen

/** Settings > Events (#73): named date ranges, such as trips, that tag their transactions. */
@Composable
fun EventsScreen(onBack: () -> Unit, viewModel: EventsViewModel = hiltViewModel()) {
    val all by viewModel.all.collectAsStateWithLifecycle()
    EventsContent(
        events = all,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onBack = onBack
    )
}

/** The events list and its dialogs, without the ViewModel, for tests. */
@Composable
fun EventsContent(
    events: List<Event>?,
    onSave: (Event, (SaveEventResult) -> Unit) -> Unit,
    onDelete: (Event) -> Unit,
    onBack: () -> Unit
) {
    // The event being edited (0 = a new one), or null; and the one being deleted.
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }

    SubScreen(
        title = stringResource(R.string.events_title),
        onBack = onBack,
        onAdd = { editingId = 0L },
        addLabel = stringResource(R.string.events_add)
    ) { padding ->
        when {
            events == null -> Unit
            events.isEmpty() -> EmptyState(
                icon = painterResource(UiR.drawable.ic_ledger),
                title = stringResource(R.string.events_empty_title),
                body = stringResource(R.string.events_empty_body),
                modifier = Modifier.padding(padding)
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = Segments.ListPadding
            ) {
                itemsIndexed(events, key = { _, row -> row.id }) { index, event ->
                    SegmentListItem(
                        index = index,
                        count = events.size,
                        headlineContent = { Text(event.name) },
                        supportingContent = { Text(eventSpan(event)) },
                        modifier = Modifier.clickable { editingId = event.id }
                    )
                }
            }
        }
    }

    val list = events.orEmpty()
    editingId?.let { id ->
        val event = list.firstOrNull { it.id == id }
        // A new event, or one still in the list (it can't be edited once gone).
        if (id == 0L || event != null) {
            EventDialog(
                event = event,
                onSave = onSave,
                onDelete = {
                    editingId = null
                    deletingId = id
                },
                onDone = { editingId = null }
            )
        }
    }
    list.firstOrNull { it.id == deletingId }?.let { event ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.events_delete_title, event.name)) },
            text = { Text(stringResource(R.string.events_delete_body, event.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(event)
                    deletingId = null
                }) { Text(stringResource(UiR.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) {
                    Text(stringResource(UiR.string.cancel))
                }
            }
        )
    }
}
