package com.openhand.khata.feature.csv

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.ui.incomeColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Counts of new, duplicate and unreadable rows, the first rows, and the import button. */
@Composable
internal fun PreviewStep(
    state: ImportState.Preview,
    onImport: () -> Unit,
    onChangeColumns: () -> Unit,
    onChooseFile: () -> Unit
) {
    val preview = state.preview
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                pluralStringResource(R.plurals.preview_new, preview.newCount, preview.newCount),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                pluralStringResource(
                    R.plurals.preview_duplicates,
                    preview.duplicateCount,
                    preview.duplicateCount
                )
            )
            Text(
                pluralStringResource(
                    R.plurals.import_invalid_count,
                    preview.invalid.size,
                    preview.invalid.size
                )
            )
        }
    }
    Button(
        onClick = onImport,
        enabled = preview.newCount > 0,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(pluralStringResource(R.plurals.preview_import, preview.newCount, preview.newCount))
    }
    if (state.matched) {
        OutlinedButton(onClick = onChangeColumns, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.preview_change_columns))
        }
    }
    OutlinedButton(onClick = onChooseFile, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.import_choose_another))
    }
    if (preview.rows.isNotEmpty()) {
        Text(
            stringResource(R.string.preview_first_rows),
            style = MaterialTheme.typography.titleMedium
        )
        Column {
            preview.rows.take(PREVIEW_ROWS).forEach { row ->
                PreviewRowItem(row)
                HorizontalDivider()
            }
        }
    }
    if (preview.invalid.isNotEmpty()) {
        Text(
            stringResource(R.string.preview_problems),
            style = MaterialTheme.typography.titleMedium
        )
        preview.invalid.take(PREVIEW_ROWS).forEach { row ->
            Text(
                stringResource(
                    R.string.preview_problem_row,
                    row.line,
                    stringResource(problemText(row.problem))
                ),
                color = MaterialTheme.colorScheme.error
            )
        }
        if (preview.invalid.size > PREVIEW_ROWS) {
            val more = preview.invalid.size - PREVIEW_ROWS
            Text(pluralStringResource(R.plurals.preview_more_problems, more, more))
        }
    }
}

@Composable
private fun PreviewRowItem(row: PreviewRow) {
    val record = row.record
    val date = Instant.ofEpochMilli(record.timestamp).atZone(ZoneId.systemDefault())
    val format = DateTimeFormatter.ofPattern(DATE_PATTERN, LocalConfiguration.current.locales[0])
    val description = listOfNotNull(
        record.payeeName ?: record.payee,
        record.note,
        record.category
    ).joinToString(" · ")
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(format.format(date), style = MaterialTheme.typography.labelMedium)
            if (description.isNotEmpty()) {
                Text(description, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (row.duplicate) {
                Text(
                    stringResource(R.string.preview_duplicate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column {
            Text(
                Money.format(record.amountPaise),
                color = if (record.direction == Direction.CREDIT) {
                    incomeColor()
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                stringResource(directionText(record.direction)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@StringRes
private fun directionText(direction: Direction): Int = when (direction) {
    Direction.DEBIT -> R.string.direction_debit
    Direction.CREDIT -> R.string.direction_credit
    Direction.REFUND -> R.string.direction_refund
    Direction.TRANSFER -> R.string.direction_transfer
}

@StringRes
private fun problemText(problem: RowProblem): Int = when (problem) {
    RowProblem.COLUMNS -> R.string.problem_columns
    RowProblem.DATE -> R.string.problem_date
    RowProblem.TIME -> R.string.problem_time
    RowProblem.AMOUNT -> R.string.problem_amount
    RowProblem.DIRECTION -> R.string.problem_direction
    RowProblem.COUNTS_IN -> R.string.problem_counts_in
}

private const val PREVIEW_ROWS = 10
private const val DATE_PATTERN = "d MMM yyyy"
