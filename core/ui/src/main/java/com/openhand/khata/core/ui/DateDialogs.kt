package com.openhand.khata.core.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// Material date pickers work in UTC midnights; these convert to and from local dates.
private fun LocalDate.toPickerMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.fromPickerMillis() =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(date: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = date.toPickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(state.selectedDateMillis?.fromPickerMillis() ?: date) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    ) { DatePicker(state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(time: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(
        initialHour = time.hour,
        initialMinute = time.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/**
 * Picks a first and last day (inclusive); one day alone is a range of one day. Used by Events,
 * Export, Insights' custom period and the Transactions date filter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(
    start: LocalDate?,
    end: LocalDate?,
    onPick: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = start?.toPickerMillis(),
        initialSelectedEndDateMillis = end?.toPickerMillis()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val first = state.selectedStartDateMillis?.fromPickerMillis()
                    if (first != null) {
                        onPick(first, state.selectedEndDateMillis?.fromPickerMillis() ?: first)
                    }
                }
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    ) {
        DateRangePicker(
            state,
            modifier = Modifier.weight(1f),
            // Material's own headline breaks the end date over several lines; this one fits.
            headline = {
                RangeHeadline(
                    state.selectedStartDateMillis?.fromPickerMillis(),
                    state.selectedEndDateMillis?.fromPickerMillis(),
                    Modifier.padding(start = 64.dp, end = 12.dp, bottom = 12.dp)
                )
            }
        )
    }
}

/**
 * "1 Oct – 4 Oct 2026": the year once when both days share it. Each date keeps together, so a
 * large font wraps only at the dash.
 */
@Composable
internal fun RangeHeadline(start: LocalDate?, end: LocalDate?, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val text = rangeText(
        start,
        end,
        locale,
        stringResource(R.string.date_range_start),
        stringResource(R.string.date_range_end)
    )
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = modifier)
}

internal fun rangeText(
    start: LocalDate?,
    end: LocalDate?,
    locale: Locale,
    noStart: String,
    noEnd: String
): String {
    val full = DateTimeFormatter.ofPattern(FULL_DATE, locale)
    val sameYear = start != null && end != null && start.year == end.year
    val first = start?.format(
        if (sameYear) DateTimeFormatter.ofPattern(DAY_MONTH, locale) else full
    )
    val last = end?.format(full)
    return (first ?: noStart).keepTogether() + " – " + (last ?: noEnd).keepTogether()
}

private fun String.keepTogether() = replace(' ', '\u00A0')

private const val FULL_DATE = "d MMM yyyy"
private const val DAY_MONTH = "d MMM"
