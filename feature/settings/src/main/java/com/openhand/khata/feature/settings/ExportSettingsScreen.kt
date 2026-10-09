package com.openhand.khata.feature.settings

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.ui.SubScreen

/** Settings > Export settings: one file, locked with a password, saved where the user picks. */
@Composable
fun ExportSettingsScreen(onBack: () -> Unit, viewModel: ExportSettingsViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    // Held only while the file picker is open; never saved.
    var password by remember { mutableStateOf("") }
    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(SETTINGS_MIME_TYPE)
    ) { uri -> uri?.let { viewModel.export(it, password) } }
    ExportSettingsContent(
        status = status,
        onExport = {
            password = it
            createFile.launch(viewModel.fileName())
        },
        onBack = onBack
    )
}

@Composable
fun ExportSettingsContent(
    status: SettingsExportStatus,
    onExport: (password: String) -> Unit,
    onBack: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val tooShort = password.length < MIN_PASSWORD_LENGTH
    val different = repeat != password
    SubScreen(title = stringResource(R.string.export_settings_title), onBack = onBack) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.export_settings_intro))
            PasswordField(
                value = password,
                onValue = { password = it },
                label = stringResource(R.string.settings_file_password),
                error = stringResource(R.string.export_settings_too_short, MIN_PASSWORD_LENGTH)
                    .takeIf { password.isNotEmpty() && tooShort }
            )
            PasswordField(
                value = repeat,
                onValue = { repeat = it },
                label = stringResource(R.string.export_settings_repeat),
                error = stringResource(R.string.export_settings_different)
                    .takeIf { repeat.isNotEmpty() && different }
            )
            NoRecoveryNote()
            Button(
                onClick = { onExport(password) },
                enabled = !tooShort && !different && status != SettingsExportStatus.Working,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.export_settings_button)) }
            ExportStatusText(status)
        }
    }
}

@Composable
internal fun PasswordField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    error: String? = null,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun NoRecoveryNote() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Text(
            stringResource(R.string.export_settings_no_recovery),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun ExportStatusText(status: SettingsExportStatus) {
    when (status) {
        SettingsExportStatus.Idle -> Unit
        SettingsExportStatus.Working -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text(stringResource(R.string.export_settings_working))
        }
        is SettingsExportStatus.Done -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.export_settings_done),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            SettingsCountLines(status.counts)
            Text(stringResource(R.string.export_settings_done_preferences))
        }
        SettingsExportStatus.Failed -> Text(
            stringResource(R.string.export_settings_failed),
            color = MaterialTheme.colorScheme.error
        )
    }
}

/** One line per kind of data in a settings file, e.g. "3 custom parsers". */
@Composable
internal fun SettingsCountLines(counts: SettingsCounts) {
    listOf(
        R.plurals.settings_file_custom_parsers to counts.customParsers,
        R.plurals.settings_file_builtin_edits to counts.builtInEdits,
        R.plurals.settings_file_ignore_rules to counts.ignoreRules,
        R.plurals.settings_categories_count to counts.categories,
        R.plurals.settings_accounts_count to counts.accounts,
        R.plurals.settings_payees_count to counts.payees,
        R.plurals.settings_events_count to counts.events
    ).forEach { (plural, count) -> Text(pluralStringResource(plural, count, count)) }
}

/** Some file managers only offer to save "binary" files; the `.khata` name says what it is. */
internal const val SETTINGS_MIME_TYPE = "application/octet-stream"
internal const val MIN_PASSWORD_LENGTH = 8
