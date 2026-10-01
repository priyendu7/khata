package com.openhand.khata.feature.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.ui.DateDialog
import com.openhand.khata.core.ui.SubScreen
import com.openhand.khata.sms.ingest.ScanProgress
import com.openhand.khata.sms.ingest.ScanSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Settings > SMS import (PRD feature 7). The explanation is on screen before the switch, and the
 * SMS permission is asked for only when the user turns it on (PRD principle 6).
 */
@Composable
fun SmsImportScreen(
    onBack: () -> Unit,
    onTestMessage: () -> Unit,
    viewModel: SmsImportViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val lastScan by viewModel.lastScan.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    var allowed by remember { mutableStateOf(context.hasSmsPermission()) }
    var permission by rememberSaveable { mutableStateOf(PermissionState.NOT_ASKED) }
    // The user may change the permission in the phone's settings and come back.
    LifecycleResumeEffect(permission) {
        allowed = context.hasSmsPermission()
        when {
            !allowed && enabled -> viewModel.setEnabled(false)
            // Back from the app settings, where the user went to allow it: that was a yes.
            allowed && permission == PermissionState.BLOCKED -> {
                permission = PermissionState.NOT_ASKED
                viewModel.setEnabled(true)
            }
        }
        onPauseOrDispose {}
    }
    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        allowed = granted.values.all { it }
        viewModel.setEnabled(allowed)
        // Android stops showing its dialog after the user refuses twice ("don't ask again").
        val canAskAgain = context.findActivity()
            ?.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) == true
        permission = when {
            allowed -> PermissionState.NOT_ASKED
            canAskAgain -> PermissionState.REFUSED
            else -> PermissionState.BLOCKED
        }
    }
    SmsImportContent(
        onBack = onBack,
        on = enabled && allowed,
        permissionStillAllowed = allowed,
        permission = permission,
        progress = progress,
        lastScan = lastScan,
        onToggle = { on ->
            when {
                !on -> viewModel.setEnabled(false)
                allowed -> viewModel.setEnabled(true)
                permission == PermissionState.BLOCKED -> context.openAppSettings()
                else -> ask.launch(SMS_PERMISSIONS)
            }
        },
        onOpenAppSettings = { context.openAppSettings() },
        onImport = viewModel::startImport,
        onCancel = viewModel::cancelImport,
        onTestMessage = onTestMessage
    )
}

enum class PermissionState { NOT_ASKED, REFUSED, BLOCKED }

@Composable
fun SmsImportContent(
    onBack: () -> Unit,
    on: Boolean,
    permissionStillAllowed: Boolean,
    permission: PermissionState,
    progress: ScanProgress?,
    lastScan: ScanSummary?,
    onToggle: (Boolean) -> Unit,
    onOpenAppSettings: () -> Unit,
    onImport: (LocalDate) -> Unit,
    onCancel: () -> Unit,
    onTestMessage: () -> Unit,
    today: LocalDate = LocalDate.now()
) {
    SubScreen(title = stringResource(R.string.sms_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Explanation()
            ListItem(
                headlineContent = { Text(stringResource(R.string.sms_switch)) },
                supportingContent = { Text(stringResource(switchSummary(on, permission))) },
                trailingContent = { Switch(checked = on, onCheckedChange = null) },
                modifier = Modifier.toggleable(
                    value = on,
                    role = Role.Switch,
                    onValueChange = onToggle
                )
            )
            val settingsHint = when {
                permission == PermissionState.BLOCKED && !on -> R.string.sms_blocked
                !on && permissionStillAllowed -> R.string.sms_still_allowed
                else -> null
            }
            settingsHint?.let { hint ->
                Hint(stringResource(hint))
                OutlinedButton(
                    onClick = onOpenAppSettings,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(stringResource(R.string.sms_open_settings))
                }
            }
            if (on) {
                HorizontalDivider(Modifier.padding(top = 16.dp))
                PastSmsImport(progress, lastScan, onImport, onCancel, today)
            }
            // Works with SMS import off too: it reads only what's pasted.
            HorizontalDivider(Modifier.padding(top = 8.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.sms_test_entry)) },
                supportingContent = { Text(stringResource(R.string.sms_test_entry_note)) },
                modifier = Modifier.clickable(onClick = onTestMessage)
            )
        }
    }
}

private fun switchSummary(on: Boolean, permission: PermissionState) = when {
    on -> R.string.sms_switch_on
    permission == PermissionState.REFUSED -> R.string.sms_refused
    else -> R.string.sms_switch_off
}

@Composable
private fun Explanation() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.sms_intro), style = MaterialTheme.typography.bodyLarge)
        listOf(
            R.string.sms_point_banks,
            R.string.sms_point_ignored,
            R.string.sms_point_private
        ).forEach {
            Text("• " + stringResource(it), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun PastSmsImport(
    progress: ScanProgress?,
    lastScan: ScanSummary?,
    onImport: (LocalDate) -> Unit,
    onCancel: () -> Unit,
    today: LocalDate
) {
    // Suggest about a month or two of history: enough for the charts, not too many new payees.
    var from by rememberSaveable { mutableStateOf(today.minusMonths(1).withDayOfMonth(1)) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val dateFormat = DateTimeFormatter.ofPattern(
        DATE_PATTERN,
        LocalConfiguration.current.locales[0]
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.sms_import_title)) },
        supportingContent = { Text(stringResource(R.string.sms_import_note)) }
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.sms_import_from)) },
        supportingContent = { Text(from.format(dateFormat)) },
        modifier = Modifier.clickable(enabled = progress == null) { picking = true }
    )
    if (progress == null) {
        Button(onClick = { onImport(from) }, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.sms_import_button))
        }
    } else {
        ImportProgress(progress, onCancel)
    }
    lastScan?.let { LastImport(it, dateFormat) }
    if (picking) {
        DateDialog(
            date = from,
            onPick = {
                from = minOf(it, today)
                picking = false
            },
            onDismiss = { picking = false }
        )
    }
}

@Composable
private fun ImportProgress(progress: ScanProgress, onCancel: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (progress.started && progress.total > 0) {
            LinearProgressIndicator(
                progress = { progress.done.toFloat() / progress.total },
                modifier = Modifier.fillMaxWidth()
            )
            Text(stringResource(R.string.sms_import_running, progress.done, progress.total))
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.sms_import_starting))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.sms_import_cancel)) }
        }
    }
}

@Composable
private fun LastImport(summary: ScanSummary, dateFormat: DateTimeFormatter) {
    val since = Instant.ofEpochMilli(summary.since).atZone(ZoneId.systemDefault()).toLocalDate()
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            stringResource(R.string.sms_last_import, since.format(dateFormat)),
            style = MaterialTheme.typography.titleSmall
        )
        Text(stringResource(R.string.sms_summary_recorded, summary.recorded))
        Text(stringResource(R.string.sms_summary_to_review, summary.toReview))
        Text(stringResource(R.string.sms_summary_already_there, summary.alreadyThere))
        Text(stringResource(R.string.sms_summary_unreadable, summary.unreadable))
        Text(stringResource(R.string.sms_summary_filtered, summary.filtered))
    }
}

private const val DATE_PATTERN = "d MMM yyyy"
