package com.openhand.khata.feature.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.model.ReminderInterval
import com.openhand.khata.core.model.ReminderSettings
import com.openhand.khata.core.ui.Choice
import com.openhand.khata.core.ui.ChoiceDialog

/**
 * Settings rows for the backup reminder (PRD feature 6). Turning it on asks for the notification
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

@Composable
fun BackupReminderRows(
    settings: ReminderSettings,
    notificationsAllowed: Boolean,
    onEnabled: (Boolean) -> Unit,
    onInterval: (ReminderInterval) -> Unit
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_reminder)) },
        supportingContent = {
            Text(
                stringResource(
                    if (settings.enabled && !notificationsAllowed) {
                        R.string.settings_reminder_home_only
                    } else {
                        R.string.settings_reminder_summary
                    }
                )
            )
        },
        trailingContent = { Switch(checked = settings.enabled, onCheckedChange = null) },
        modifier = Modifier.toggleable(
            value = settings.enabled,
            role = Role.Switch,
            onValueChange = onEnabled
        )
    )
    if (settings.enabled) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_reminder_interval)) },
            supportingContent = { Text(intervalLabel(settings.interval)) },
            modifier = Modifier.clickable { choosing = true }
        )
    }
    if (choosing) {
        ChoiceDialog(
            title = stringResource(R.string.settings_reminder_interval),
            choices = ReminderInterval.entries.map { Choice(it, intervalLabel(it)) },
            selected = settings.interval,
            onSelect = {
                choosing = false
                onInterval(it)
            },
            onDismiss = { choosing = false }
        )
    }
}

@Composable
private fun intervalLabel(interval: ReminderInterval) =
    pluralStringResource(R.plurals.settings_reminder_days, interval.days, interval.days)
