package com.openhand.khata.feature.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.ui.DateDialog
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.SegmentListItem
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.SubScreen
import com.openhand.khata.sms.ingest.ScanProgress
import com.openhand.khata.sms.ingest.ScanSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Settings > SMS import (PRD feature 7). The explanation sits right under the switch, and the
 * SMS permission is asked for only when the user turns it on (PRD principle 6). Filters and Test
 * a message are reached from Settings' SMS section.
 */
@Composable
fun SmsImportScreen(onBack: () -> Unit, viewModel: SmsImportViewModel = hiltViewModel()) {
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
        onCancel = viewModel::cancelImport
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
    today: LocalDate = LocalDate.now()
) {
    SubScreen(title = stringResource(R.string.sms_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = Segments.Inset, end = Segments.Inset, bottom = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            SegmentListItem(
                index = 0,
                count = 1,
                headlineContent = { Text(stringResource(R.string.sms_switch)) },
                supportingContent = { Text(stringResource(switchSummary(on, permission))) },
                trailingContent = { Switch(checked = on, onCheckedChange = null) },
                modifier = Modifier.toggleable(
                    value = on,
                    role = Role.Switch,
                    onValueChange = onToggle
                )
            )
            Explanation()
            val settingsHint = when {
                permission == PermissionState.BLOCKED && !on -> R.string.sms_blocked
                !on && permissionStillAllowed -> R.string.sms_still_allowed
                else -> null
            }
            settingsHint?.let { hint ->
                Hint(stringResource(hint))
                OutlinedButton(
                    onClick = onOpenAppSettings,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.sms_open_settings))
                }
            }
            if (on) {
                // Two separate things: reading new SMS, and a one-off import of old ones.
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                PastSmsImport(progress, lastScan, onImport, onCancel, today)
            }
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
        // On the same edge as the import section below, not indented under the switch's text.
        modifier = Modifier.padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Text(
            stringResource(R.string.sms_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
        listOf(
            R.string.sms_point_banks,
            R.string.sms_point_ignored,
            R.string.sms_point_private
        ).forEach {
            Text(
                "• " + stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                color = color
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
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
    Text(stringResource(R.string.sms_import_title), style = MaterialTheme.typography.titleMedium)
    Hint(stringResource(R.string.sms_import_note))
    // An outlined button with a calendar, so the date reads as something to tap.
    OutlinedButton(
        onClick = { picking = true },
        enabled = progress == null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.sms_import_from), modifier = Modifier.weight(1f))
        Icon(
            painterResource(UiR.drawable.ic_calendar),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(from.format(dateFormat))
    }
    if (progress == null) {
        Button(onClick = { onImport(from) }, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.sms_import_button))
        }
    } else {
        ImportProgress(progress, onCancel)
    }
    // The counts below are for this manual import only; new SMS don't wait for it.
    Hint(stringResource(R.string.sms_live_note))
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
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    val since = summary.since.toLocalDate()
    val ran = summary.finishedAt.toLocalDate()
    Column(
        modifier = Modifier.padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            stringResource(
                R.string.sms_last_import,
                ran.format(dateFormat),
                since.format(dateFormat)
            ),
            style = MaterialTheme.typography.titleSmall
        )
        Text(stringResource(R.string.sms_summary_recorded, summary.recorded))
        Text(stringResource(R.string.sms_summary_to_review, summary.toReview))
        Text(stringResource(R.string.sms_summary_already_there, summary.alreadyThere))
        Text(stringResource(R.string.sms_summary_unreadable, summary.unreadable))
        Text(stringResource(R.string.sms_summary_filtered, summary.filtered))
    }
}

private fun Long.toLocalDate() =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private const val DATE_PATTERN = "d MMM yyyy"
