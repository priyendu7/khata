package com.openhand.khata.feature.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.data.SaveEventResult
import com.openhand.khata.core.model.Event
import com.openhand.khata.core.ui.DateRangeDialog
import com.openhand.khata.core.ui.PickerField
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.TimeDialog
import com.openhand.khata.core.ui.timeLabel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Adds an event ([event] null) or changes one: its name, its first and last day and, if set, the
 * time it starts on the first day and ends on the last (#141).
 */
@Composable
internal fun EventDialog(
    event: Event?,
    onSave: (Event, (SaveEventResult) -> Unit) -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(event?.name.orEmpty()) }
    var start by rememberSaveable { mutableStateOf(event?.start) }
    var end by rememberSaveable { mutableStateOf(event?.end) }
    var timed by rememberSaveable { mutableStateOf(event?.hasTimes == true) }
    var startTime by rememberSaveable { mutableStateOf(event?.startTime ?: LocalTime.MIDNIGHT) }
    var endTime by rememberSaveable { mutableStateOf(event?.endTime ?: LAST_MINUTE) }
    var nameTaken by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf<Picking?>(null) }
    // What Save would save, once there are dates.
    val draft = draftOf(start, end, startTime.takeIf { timed }, endTime)
        ?.copy(id = event?.id ?: 0, name = name)
    val badTimes = draft?.timesInOrder == false

    AlertDialog(
        onDismissRequest = onDone,
        title = {
            Text(stringResource(if (event == null) R.string.events_add else R.string.events_edit))
        },
        text = {
            // Taller with times; it scrolls on a small screen or with a large font.
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NameField(name, nameTaken) {
                    name = it
                    nameTaken = false
                }
                DatesField(start, end) { picking = Picking.DATES }
                TimesSwitch(timed) { timed = it }
                if (timed) {
                    TimeFields(
                        startTime,
                        endTime,
                        badTimes,
                        onPickStart = { picking = Picking.START },
                        onPickEnd = { picking = Picking.END }
                    )
                }
                Text(
                    stringResource(R.string.events_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && draft != null && !badTimes,
                onClick = {
                    draft?.let {
                        onSave(it) { result ->
                            if (result == SaveEventResult.NameTaken) nameTaken = true
                            if (result is SaveEventResult.Saved) onDone()
                        }
                    }
                }
            ) { Text(stringResource(UiR.string.save)) }
        },
        dismissButton = { DismissButtons(onDelete.takeIf { event != null }, onDone) }
    )
    picking?.let { what ->
        PickerDialog(
            what,
            start,
            end,
            startTime,
            endTime,
            onDates = { from, to ->
                start = from
                end = to
            },
            onStartTime = { startTime = it },
            onEndTime = { endTime = it },
            onClose = { picking = null }
        )
    }
}

private enum class Picking { DATES, START, END }

/** The event's dates and times as Save would save them, or null until it has dates. */
private fun draftOf(start: LocalDate?, end: LocalDate?, startTime: LocalTime?, endTime: LocalTime) =
    if (start == null || end == null) {
        null
    } else {
        Event(
            name = "",
            start = start,
            end = end,
            startTime = startTime,
            endTime = endTime.takeIf { startTime != null }
        )
    }

@Composable
private fun DatesField(start: LocalDate?, end: LocalDate?, onClick: () -> Unit) {
    PickerField(
        label = stringResource(R.string.events_dates),
        value = if (start != null && end != null) {
            dateRange(start, end)
        } else {
            stringResource(R.string.events_pick_dates)
        },
        onClick = onClick
    )
}

/** Delete (when [onDelete] is set, for a saved event) and Cancel. */
@Composable
private fun DismissButtons(onDelete: (() -> Unit)?, onCancel: () -> Unit) {
    Row {
        if (onDelete != null) {
            TextButton(onClick = onDelete) { Text(stringResource(UiR.string.delete)) }
        }
        TextButton(onClick = onCancel) { Text(stringResource(UiR.string.cancel)) }
    }
}

/** The dates or one of the times, being picked; each pick closes the dialog. */
@Composable
private fun PickerDialog(
    picking: Picking,
    start: LocalDate?,
    end: LocalDate?,
    startTime: LocalTime,
    endTime: LocalTime,
    onDates: (LocalDate, LocalDate) -> Unit,
    onStartTime: (LocalTime) -> Unit,
    onEndTime: (LocalTime) -> Unit,
    onClose: () -> Unit
) {
    when (picking) {
        Picking.DATES -> DateRangeDialog(start, end, { from, to ->
            onDates(from, to)
            onClose()
        }, onClose)
        Picking.START -> TimeDialog(startTime, {
            onStartTime(it)
            onClose()
        }, onClose)
        Picking.END -> TimeDialog(endTime, {
            onEndTime(it)
            onClose()
        }, onClose)
    }
}

@Composable
private fun NameField(name: String, taken: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = name,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.events_name)) },
        placeholder = { Text(stringResource(R.string.events_name_hint)) },
        isError = taken,
        supportingText = if (taken) {
            { Text(stringResource(R.string.events_name_taken)) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Off by default: the event is whole days unless times are set. */
@Composable
private fun TimesSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
    ) {
        Text(stringResource(R.string.events_set_times), Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = null)
    }
}

@Composable
private fun TimeFields(
    start: LocalTime,
    end: LocalTime,
    endBeforeStart: Boolean,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit
) {
    PickerField(
        label = stringResource(R.string.events_start_time),
        value = timeLabel(start),
        onClick = onPickStart
    )
    PickerField(
        label = stringResource(R.string.events_end_time),
        value = timeLabel(end),
        onClick = onPickEnd
    )
    if (endBeforeStart) {
        Text(
            stringResource(R.string.events_end_before_start),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * "2 Oct 2026 – 4 Oct 2026", or just the day for a one-day event. With times, "2 Oct, 6:00 PM –
 * 4 Oct, 11:00 PM" (the year shown when it isn't this year), or "2 Oct, 6:00 PM – 11:00 PM".
 */
@Composable
internal fun eventSpan(event: Event): String {
    val from = event.startTime
    val until = event.endTime
    if (from == null || until == null) return dateRange(event.start, event.end)
    val locale = LocalConfiguration.current.locales[0]
    val thisYear = LocalDate.now().year
    val thisYearOnly = event.start.year == thisYear && event.end.year == thisYear
    val format = DateTimeFormatter.ofPattern(if (thisYearOnly) DAY_MONTH else DATE_PATTERN, locale)
    val first = stringResource(R.string.events_at, format.format(event.start), timeLabel(from))
    val last = if (event.start == event.end) {
        timeLabel(until)
    } else {
        stringResource(R.string.events_at, format.format(event.end), timeLabel(until))
    }
    return stringResource(R.string.events_range, first, last)
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

/** The default end time: the whole last day. */
private val LAST_MINUTE = LocalTime.MAX.truncatedTo(ChronoUnit.MINUTES)

private const val DATE_PATTERN = "d MMM yyyy"
private const val DAY_MONTH = "d MMM"
