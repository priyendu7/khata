package com.openhand.khata.feature.csv

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.ui.CategoryColors
import com.openhand.khata.core.ui.SubScreen

/** Settings > Import: a CSV file from Khata or any other app, previewed before it's saved. */
@Composable
fun ImportScreen(onBack: () -> Unit, viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val names = remember(context) { CategoryNames.from(context) }
    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::open)
    }
    ImportContent(
        state = state,
        onChooseFile = { openFile.launch(CSV_MIME_TYPES) },
        onMapping = viewModel::changeMapping,
        onConfirmMapping = viewModel::confirmMapping,
        onChangeColumns = viewModel::backToMatching,
        onImport = { viewModel.import(names, CategoryColors) },
        onRestart = viewModel::restart,
        onBack = onBack
    )
}

@Composable
fun ImportContent(
    state: ImportState,
    onChooseFile: () -> Unit,
    onMapping: (ColumnMapping) -> Unit,
    onConfirmMapping: () -> Unit,
    onChangeColumns: () -> Unit,
    onImport: () -> Unit,
    onRestart: () -> Unit,
    onBack: () -> Unit
) {
    SubScreen(title = stringResource(R.string.import_title), onBack = onBack) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            when (state) {
                ImportState.Start -> StartStep(onChooseFile)
                ImportState.Working -> Box(Modifier.fillMaxWidth(), Alignment.Center) {
                    CircularProgressIndicator(Modifier.padding(32.dp))
                }
                is ImportState.Matching -> MatchingStep(state, onMapping, onConfirmMapping)
                is ImportState.Preview -> PreviewStep(
                    state = state,
                    onImport = onImport,
                    onChangeColumns = onChangeColumns,
                    onChooseFile = onChooseFile
                )
                is ImportState.Done -> DoneStep(state, onRestart, onBack)
                is ImportState.Failed -> FailedStep(state.reason, onChooseFile)
            }
        }
    }
}

@Composable
private fun StartStep(onChooseFile: () -> Unit) {
    Text(stringResource(R.string.import_intro), style = MaterialTheme.typography.bodyLarge)
    Text(
        stringResource(R.string.import_restore_order),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Button(onClick = onChooseFile, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.import_choose_file))
    }
}

@Composable
private fun DoneStep(state: ImportState.Done, onRestart: () -> Unit, onBack: () -> Unit) {
    Text(
        pluralStringResource(R.plurals.import_added, state.result.added, state.result.added),
        style = MaterialTheme.typography.titleLarge
    )
    if (state.result.duplicates > 0) {
        Text(
            pluralStringResource(
                R.plurals.import_skipped,
                state.result.duplicates,
                state.result.duplicates
            )
        )
    }
    if (state.invalid > 0) {
        Text(pluralStringResource(R.plurals.import_invalid_count, state.invalid, state.invalid))
    }
    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.import_done))
    }
    OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.import_another))
    }
}

@Composable
private fun FailedStep(reason: ImportState.Failed.Reason, onChooseFile: () -> Unit) {
    Text(
        stringResource(
            when (reason) {
                ImportState.Failed.Reason.UNREADABLE -> R.string.import_failed_unreadable
                ImportState.Failed.Reason.EMPTY -> R.string.import_failed_empty
                ImportState.Failed.Reason.SAVE -> R.string.import_failed_save
            }
        ),
        color = MaterialTheme.colorScheme.error
    )
    Button(onClick = onChooseFile, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.import_choose_another))
    }
}

/** Some file managers label CSV files as plain text or as an Excel type. */
private val CSV_MIME_TYPES =
    arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")
