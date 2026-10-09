package com.openhand.khata.feature.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.data.SaveEventResult
import com.openhand.khata.core.model.Event
import com.openhand.khata.core.ui.DateRangeDialog
import com.openhand.khata.core.ui.EmptyState
import com.openhand.khata.core.ui.PickerField
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.SegmentListItem
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.SubScreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
                        supportingContent = { Text(dateRange(event.start, event.end)) },
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

/** Adds an event ([event] null) or changes one: its name and its first and last day. */
@Composable
private fun EventDialog(
    event: Event?,
    onSave: (Event, (SaveEventResult) -> Unit) -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(event?.name.orEmpty()) }
    var start by rememberSaveable { mutableStateOf(event?.start) }
    var end by rememberSaveable { mutableStateOf(event?.end) }
    var nameTaken by rememberSaveable { mutableStateOf(false) }
    var pickingDates by rememberSaveable { mutableStateOf(false) }
    val first = start
    val last = end

    AlertDialog(
        onDismissRequest = onDone,
        title = {
            Text(stringResource(if (event == null) R.string.events_add else R.string.events_edit))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameTaken = false
                    },
                    label = { Text(stringResource(R.string.events_name)) },
                    placeholder = { Text(stringResource(R.string.events_name_hint)) },
                    isError = nameTaken,
                    supportingText = if (nameTaken) {
                        { Text(stringResource(R.string.events_name_taken)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                PickerField(
                    label = stringResource(R.string.events_dates),
                    value = if (first != null && last != null) {
                        dateRange(first, last)
                    } else {
                        stringResource(R.string.events_pick_dates)
                    },
                    onClick = { pickingDates = true }
                )
                Text(
                    stringResource(R.string.events_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && first != null && last != null,
                onClick = {
                    if (first != null && last != null) {
                        val saved =
                            Event(id = event?.id ?: 0, name = name, start = first, end = last)
                        onSave(saved) { result ->
                            if (result == SaveEventResult.NameTaken) nameTaken = true else onDone()
                        }
                    }
                }
            ) { Text(stringResource(UiR.string.save)) }
        },
        dismissButton = {
            Row {
                if (event != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(UiR.string.delete)) }
                }
                TextButton(onClick = onDone) { Text(stringResource(UiR.string.cancel)) }
            }
        }
    )
    if (pickingDates) {
        DateRangeDialog(
            start = start,
            end = end,
            onPick = { from, to ->
                start = from
                end = to
                pickingDates = false
            },
            onDismiss = { pickingDates = false }
        )
    }
}

/** "2 Oct 2026 – 4 Oct 2026", or just the day for a one-day event. */
@Composable
private fun dateRange(start: LocalDate, end: LocalDate): String {
    val format = DateTimeFormatter.ofPattern(DATE_PATTERN, LocalConfiguration.current.locales[0])
    return if (start == end) {
        format.format(start)
    } else {
        stringResource(R.string.events_range, format.format(start), format.format(end))
    }
}

private const val DATE_PATTERN = "d MMM yyyy"
