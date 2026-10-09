package com.openhand.khata.feature.settings

import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.openhand.khata.core.security.lock.LockMethod
import com.openhand.khata.core.security.lock.LockTimeout

/** Each preference in the file, with its value here and in the file. */
@Composable
internal fun PreferenceRows(current: AppPrefs, file: AppPrefs) {
    val rows = listOfNotNull(
        row(R.string.filters_title, current.smsFilters, file.smsFilters) {
            filtersText(filtersOn(it))
        },
        row(R.string.settings_reminder, current.backupReminder, file.backupReminder) { onOff(it) },
        row(R.string.settings_reminder_interval, current.reminderInterval, file.reminderInterval) {
            reminderDays(it.days)
        },
        row(R.string.settings_language, current.language, file.language) { languageName(it) },
        row(R.string.import_settings_app_lock, current.appLock, file.appLock) { onOff(it) },
        row(R.string.import_settings_lock_method, current.lockMethod, file.lockMethod) {
            lockMethod(it)
        },
        row(R.string.import_settings_lock_timeout, current.lockTimeout, file.lockTimeout) {
            lockTimeout(it)
        },
        row(
            R.string.import_settings_block_screenshots,
            current.blockScreenshots,
            file.blockScreenshots
        ) { onOff(it) }
    )
    if (rows.isEmpty()) PreviewNote(stringResource(R.string.import_settings_none))
    rows.forEach { row ->
        ListItem(
            headlineContent = { Text(row.title) },
            supportingContent = {
                Text(
                    if (row.current == row.file) {
                        stringResource(R.string.import_settings_pref_same, row.file)
                    } else {
                        stringResource(
                            R.string.import_settings_pref_change,
                            row.current.orEmpty(),
                            row.file
                        )
                    }
                )
            }
        )
    }
}

private data class PreferenceRow(val title: String, val current: String?, val file: String)

/** A row for a preference the file has; null when it doesn't. */
@Composable
private fun <T : Any> row(
    title: Int,
    current: T?,
    file: T?,
    text: @Composable (T) -> String
): PreferenceRow? = file?.let {
    PreferenceRow(stringResource(title), current?.let { here -> text(here) }, text(it))
}

@Composable
private fun onOff(on: Boolean) =
    stringResource(if (on) R.string.settings_on else R.string.settings_off)

@Composable
private fun filtersText(on: Int) =
    stringResource(R.string.import_settings_filters_on, on, FilterSwitch.entries.size)

@Composable
private fun reminderDays(days: Int) =
    pluralStringResource(R.plurals.import_settings_reminder_days, days, days)

@Composable
private fun lockMethod(method: LockMethod) = stringResource(
    when (method) {
        LockMethod.DEVICE -> R.string.import_settings_lock_device
        LockMethod.PIN -> R.string.import_settings_lock_pin
    }
)

@Composable
private fun lockTimeout(timeout: LockTimeout) = stringResource(
    when (timeout) {
        LockTimeout.IMMEDIATELY -> R.string.import_settings_timeout_immediately
        LockTimeout.SECONDS_30 -> R.string.import_settings_timeout_30s
        LockTimeout.MINUTE_1 -> R.string.import_settings_timeout_1m
        LockTimeout.MINUTES_5 -> R.string.import_settings_timeout_5m
    }
)
