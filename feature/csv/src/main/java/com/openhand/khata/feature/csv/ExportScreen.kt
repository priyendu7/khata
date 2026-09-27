package com.openhand.khata.feature.csv

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.ui.DateRangeDialog
import com.openhand.khata.core.ui.SubScreen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Settings > Export: all data or a date range, saved wherever the user picks. */
@Composable
fun ExportScreen(onBack: () -> Unit, viewModel: ExportViewModel = hiltViewModel()) {
    val range by viewModel.range.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val lastExport by viewModel.lastExport.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val names = remember(context) { CategoryNames.from(context) }
    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(CSV_MIME_TYPE)
    ) { uri -> uri?.let { viewModel.export(it, names) } }
    ExportContent(
        range = range,
        status = status,
        lastExport = lastExport,
        onRange = viewModel::setRange,
        onExport = { createFile.launch(viewModel.fileName()) },
        onBack = onBack
    )
}

@Composable
fun ExportContent(
    range: DateRange?,
    status: ExportStatus,
    lastExport: Long?,
    onRange: (DateRange?) -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit
) {
    var pickingRange by rememberSaveable { mutableStateOf(false) }
    val dateFormat = DateTimeFormatter.ofPattern(
        DATE_PATTERN,
        LocalConfiguration.current.locales[0]
    )
    SubScreen(title = stringResource(R.string.export_title), onBack = onBack) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            UnencryptedWarning()
            Text(stringResource(R.string.export_what), style = MaterialTheme.typography.titleMedium)
            Column {
                Option(
                    text = stringResource(R.string.export_all),
                    selected = range == null,
                    onClick = { onRange(null) }
                )
                Option(
                    text = range?.let {
                        stringResource(
                            R.string.export_range_value,
                            dateFormat.format(it.first),
                            dateFormat.format(it.last)
                        )
                    } ?: stringResource(R.string.export_range),
                    selected = range != null,
                    onClick = { pickingRange = true }
                )
            }
            Text(
                lastExport?.let {
                    val day = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                    stringResource(R.string.export_last, dateFormat.format(day))
                } ?: stringResource(R.string.export_never),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onExport,
                enabled = status != ExportStatus.Working,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.export_button)) }
            ExportStatusText(status)
        }
    }
    if (pickingRange) {
        DateRangeDialog(
            start = range?.first,
            end = range?.last,
            onPick = { first, last ->
                pickingRange = false
                onRange(DateRange(first, last))
            },
            onDismiss = { pickingRange = false }
        )
    }
}

@Composable
private fun UnencryptedWarning() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Text(
            stringResource(R.string.export_unencrypted),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun Option(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text)
    }
}

@Composable
private fun ExportStatusText(status: ExportStatus) {
    when (status) {
        ExportStatus.Idle -> Unit
        ExportStatus.Working -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text(stringResource(R.string.export_working))
        }
        is ExportStatus.Done -> Text(
            pluralStringResource(R.plurals.export_done, status.count, status.count),
            color = MaterialTheme.colorScheme.primary
        )
        ExportStatus.Failed -> Text(
            stringResource(R.string.export_failed),
            color = MaterialTheme.colorScheme.error
        )
    }
}

internal const val CSV_MIME_TYPE = "text/csv"
private const val DATE_PATTERN = "d MMM yyyy"
