package com.openhand.khata.feature.settings

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.ui.SubScreen

/** Settings > Import settings: a settings file, its password, a preview, then all at once. */
@Composable
fun ImportSettingsScreen(
    onBack: () -> Unit,
    next: ImportSettingsNext = ImportSettingsNext(),
    viewModel: ImportSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::open) }
    ImportSettingsContent(
        state = state,
        onChooseFile = { openFile.launch(arrayOf("*/*")) },
        onUnlock = viewModel::unlock,
        onKeep = viewModel::keep,
        onKeepAll = viewModel::keepAll,
        onImport = viewModel::import,
        onBack = onBack,
        next = next
    )
}

@Composable
fun ImportSettingsContent(
    state: SettingsImportState,
    onChooseFile: () -> Unit,
    onUnlock: (password: String) -> Unit,
    onKeep: (ruleId: String, builtIn: Boolean, keep: Boolean) -> Unit,
    onKeepAll: (keep: Boolean) -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
    next: ImportSettingsNext = ImportSettingsNext()
) {
    SubScreen(title = stringResource(R.string.import_settings_title), onBack = onBack) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            when (state) {
                SettingsImportState.Start -> StartStep(onChooseFile)
                SettingsImportState.Working -> Box(Modifier.fillMaxWidth(), Alignment.Center) {
                    CircularProgressIndicator(Modifier.padding(32.dp))
                }
                is SettingsImportState.Password -> PasswordStep(state, onUnlock, onChooseFile)
                is SettingsImportState.Preview ->
                    SettingsPreviewStep(state, onKeep, onKeepAll, onImport)
                is SettingsImportState.Done -> DoneStep(state, onBack, next)
                is SettingsImportState.Failed -> FailedStep(state.reason, onChooseFile)
            }
        }
    }
}

@Composable
private fun StartStep(onChooseFile: () -> Unit) {
    Text(stringResource(R.string.import_settings_intro), style = MaterialTheme.typography.bodyLarge)
    Text(
        stringResource(R.string.import_settings_order),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Button(onClick = onChooseFile, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.import_settings_choose))
    }
}

@Composable
private fun PasswordStep(
    state: SettingsImportState.Password,
    onUnlock: (String) -> Unit,
    onChooseFile: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    Text(stringResource(R.string.import_settings_password_intro))
    PasswordField(
        value = password,
        onValue = { password = it },
        label = stringResource(R.string.settings_file_password),
        error = stringResource(R.string.import_settings_wrong_password)
            .takeIf { state.wrongPassword }
    )
    Button(
        onClick = { onUnlock(password) },
        enabled = password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth()
    ) { Text(stringResource(R.string.import_settings_open)) }
    OutlinedButton(onClick = onChooseFile, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.import_settings_choose_another))
    }
}

@Composable
private fun DoneStep(
    state: SettingsImportState.Done,
    onBack: () -> Unit,
    next: ImportSettingsNext
) {
    Text(stringResource(R.string.import_settings_done), style = MaterialTheme.typography.titleLarge)
    if (state.readAgain > 0) {
        val count = state.readAgain
        Text(pluralStringResource(R.plurals.import_settings_read_again, count, count))
    }
    if (state.outcome.needsPin) {
        Text(stringResource(R.string.import_settings_needs_pin))
        OutlinedButton(onClick = next.onAppLock, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.import_settings_set_pin))
        }
    }
    if (state.outcome.offerSmsImport) {
        Text(stringResource(R.string.import_settings_sms_was_on))
        OutlinedButton(onClick = next.onSmsImport, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.import_settings_turn_on_sms))
        }
    }
    Text(stringResource(R.string.import_settings_next_transactions))
    Button(onClick = next.onImportTransactions, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_import))
    }
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.import_settings_finish))
    }
}

@Composable
private fun FailedStep(reason: SettingsImportState.Failed.Reason, onChooseFile: () -> Unit) {
    Text(
        stringResource(
            when (reason) {
                SettingsImportState.Failed.Reason.UNREADABLE -> R.string.import_settings_unreadable
                SettingsImportState.Failed.Reason.NOT_SETTINGS_FILE ->
                    R.string.import_settings_not_settings
                SettingsImportState.Failed.Reason.NEWER_VERSION -> R.string.import_settings_newer
                SettingsImportState.Failed.Reason.DAMAGED -> R.string.import_settings_damaged
                SettingsImportState.Failed.Reason.SAVE -> R.string.import_settings_save_failed
            }
        ),
        color = MaterialTheme.colorScheme.error
    )
    Button(onClick = onChooseFile, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.import_settings_choose_another))
    }
}
