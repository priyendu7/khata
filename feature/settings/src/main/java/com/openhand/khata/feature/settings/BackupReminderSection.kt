package com.openhand.khata.feature.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.model.ReminderInterval
import com.openhand.khata.core.model.ReminderSettings
import com.openhand.khata.core.ui.R as UiR

/**
 * The Settings row for the backup reminder (PRD feature 6). Turning it on asks for the notification
 * permission on Android 13+; if refused, the reminder still shows on Home.
 */
@Composable
fun BackupReminderSection(viewModel: BackupReminderViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val areEnabled = { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    var notificationsAllowed by remember { mutableStateOf(areEnabled()) }
    // The user may change it in the phone's settings and come back.
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = areEnabled()
        onPauseOrDispose {}
    }
    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notificationsAllowed = areEnabled() }
    BackupReminderRows(
        settings = settings,
        notificationsAllowed = notificationsAllowed,
        onEnabled = { on ->
            viewModel.setEnabled(on)
            if (on &&
                !notificationsAllowed &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ) {
                askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onInterval = viewModel::setInterval
    )
}

/** One row showing the reminder's state; it opens a dialog to switch it and pick the interval. */
@Composable
fun BackupReminderRows(
    settings: ReminderSettings,
    notificationsAllowed: Boolean,
    onEnabled: (Boolean) -> Unit,
    onInterval: (ReminderInterval) -> Unit
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val state = if (settings.enabled) {
        intervalLabel(
            settings.interval
        )
    } else {
        stringResource(R.string.settings_off)
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_reminder)) },
        supportingContent = {
            Text(
                if (settings.enabled && !notificationsAllowed) {
                    pair(state, stringResource(R.string.settings_reminder_home_only_short))
                } else {
                    state
                }
            )
        },
        modifier = Modifier.clickable { editing = true }
    )
    if (editing) {
        ReminderDialog(
            settings = settings,
            notificationsAllowed = notificationsAllowed,
            onEnabled = onEnabled,
            onInterval = onInterval,
            onDismiss = { editing = false }
        )
    }
}

/** Changes apply as they're made, so the dialog only needs a button to close it. */
@Composable
private fun ReminderDialog(
    settings: ReminderSettings,
    notificationsAllowed: Boolean,
    onEnabled: (Boolean) -> Unit,
    onInterval: (ReminderInterval) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reminder)) },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_reminder_summary)) },
                    supportingContent = if (settings.enabled && !notificationsAllowed) {
                        { Text(stringResource(R.string.settings_reminder_home_only)) }
                    } else {
                        null
                    },
                    trailingContent = {
                        Switch(checked = settings.enabled, onCheckedChange = null)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.toggleable(
                        value = settings.enabled,
                        role = Role.Switch,
                        onValueChange = onEnabled
                    )
                )
                if (settings.enabled) {
                    Text(
                        stringResource(R.string.settings_reminder_interval),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    ReminderInterval.entries.forEach { interval ->
                        IntervalOption(
                            label = intervalLabel(interval),
                            selected = interval == settings.interval,
                            onSelect = { onInterval(interval) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.ok)) }
        }
    )
}

@Composable
private fun IntervalOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun intervalLabel(interval: ReminderInterval) =
    pluralStringResource(R.plurals.settings_reminder_days, interval.days, interval.days)
