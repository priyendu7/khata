package com.openhand.khata.feature.insights

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.model.CardReminderSettings
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.RowChevron
import com.openhand.khata.core.ui.RowIcon
import com.openhand.khata.core.ui.Tone

/**
 * The row for card due date reminders (#156), on the Cards tab and in Settings. Turning them on
 * asks for the notification permission on Android 13+; if refused, the Home card is the reminder.
 */
@Composable
fun CardReminderSection(
    modifier: Modifier = Modifier,
    viewModel: CardReminderViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val allowed = rememberNotificationsAllowed(viewModel::recheckNotifications)
    val ask = rememberAskNotifications(viewModel::recheckNotifications)
    CardReminderRow(
        settings = settings,
        notificationsAllowed = allowed,
        onEnabled = { on ->
            viewModel.setEnabled(on)
            if (on) ask()
        },
        onDay = viewModel::setDay,
        modifier = modifier
    )
}

/** One row with the reminders' state; it opens a dialog to switch them and pick the days. */
@Composable
fun CardReminderRow(
    settings: CardReminderSettings,
    notificationsAllowed: Boolean,
    onEnabled: (Boolean) -> Unit,
    onDay: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val state = when {
        !settings.enabled -> stringResource(R.string.card_reminder_off)
        !notificationsAllowed -> stringResource(R.string.card_reminder_home_only_short)
        else -> daysSummary(settings.daysBefore)
    }
    ListItem(
        leadingContent = { RowIcon(UiR.drawable.ic_notifications, Tone.INFO) },
        headlineContent = {
            Text(stringResource(R.string.card_reminder_setting), fontWeight = FontWeight.SemiBold)
        },
        trailingContent = { RowChevron() },
        supportingContent = { Text(state) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable { editing = true }
    )
    if (editing) {
        ReminderDialog(
            settings = settings,
            notificationsAllowed = notificationsAllowed,
            onEnabled = onEnabled,
            onDay = onDay,
            onDismiss = { editing = false }
        )
    }
}

/** Changes apply as they're made, so the dialog only needs a button to close it. */
@Composable
private fun ReminderDialog(
    settings: CardReminderSettings,
    notificationsAllowed: Boolean,
    onEnabled: (Boolean) -> Unit,
    onDay: (Int, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.card_reminder_setting)) },
        text = {
            Column {
                DialogRow(
                    Modifier.toggleable(
                        value = settings.enabled,
                        role = Role.Switch,
                        onValueChange = onEnabled
                    )
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.card_reminder_summary))
                        if (settings.enabled && !notificationsAllowed) {
                            Text(
                                stringResource(R.string.card_reminder_home_only),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(checked = settings.enabled, onCheckedChange = null)
                }
                if (settings.enabled) {
                    Text(
                        stringResource(R.string.card_reminder_when),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                    CardReminderSettings.DAY_CHOICES.forEach { days ->
                        val on = days in settings.daysBefore
                        DialogRow(
                            Modifier.toggleable(
                                value = on,
                                role = Role.Checkbox,
                                onValueChange = { onDay(days, it) }
                            )
                        ) {
                            Checkbox(checked = on, onCheckedChange = null)
                            Text(dayLabel(days))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.ok)) }
        }
    )
}

/** The dialog's one row style, for the switch and the days alike, so they line up. */
@Composable
private fun DialogRow(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(modifier)
            .padding(vertical = 4.dp),
        content = content
    )
}

/** "3 days before, 1 day before, on the due date". */
@Composable
private fun daysSummary(days: Set<Int>): String {
    if (days.isEmpty()) return stringResource(R.string.card_reminder_no_days)
    return days.sortedDescending().map { dayLabel(it) }.joinToString(", ")
}

/** "3 days before", or "on the due date" for 0. */
@Composable
internal fun dayLabel(days: Int): String = if (days == 0) {
    stringResource(R.string.card_reminder_on_the_day)
} else {
    pluralStringResource(R.plurals.card_reminder_days_before, days, days)
}

/**
 * Whether Khata may post notifications. The user may change it in the phone's settings and come
 * back, so it's read again on resume, and [onResume] tells the scheduler.
 */
@Composable
internal fun rememberNotificationsAllowed(onResume: () -> Unit): Boolean {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(CardDueNotifier.canNotify(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = CardDueNotifier.canNotify(context)
        onResume()
        onPauseOrDispose {}
    }
    return allowed
}

/** Asks for the notification permission on Android 13+ when it's missing; [onResult] after. */
@Composable
internal fun rememberAskNotifications(onResult: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onResult() }
    return {
        if (!CardDueNotifier.canNotify(context) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
