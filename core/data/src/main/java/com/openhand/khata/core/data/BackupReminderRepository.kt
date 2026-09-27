package com.openhand.khata.core.data

import android.content.Context
import com.openhand.khata.core.model.BackupReminder
import com.openhand.khata.core.model.ReminderInterval
import com.openhand.khata.core.model.ReminderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * The backup reminder (PRD feature 6): its setting, and whether it's due. The setting lives in
 * app-private SharedPreferences, not the encrypted database, so scheduling it at startup doesn't
 * open the database; it holds no personal data. Android backup is off for the whole app.
 */
@Singleton
class BackupReminderRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val backup: BackupRepository,
    private val transactions: TransactionRepository
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<ReminderSettings> = _settings.asStateFlow()

    fun setEnabled(enabled: Boolean) = save(_settings.value.copy(enabled = enabled))

    fun setInterval(interval: ReminderInterval) = save(_settings.value.copy(interval = interval))

    /** Whether Home should show the reminder, updated as exports and transactions happen. */
    fun observeDue(now: () -> Long = System::currentTimeMillis): Flow<Boolean> = combine(
        settings,
        backup.observeLastExport(),
        transactions.observeAny()
    ) { settings, lastExport, any ->
        BackupReminder.isDue(settings, lastExport, countingSince(), any, now())
    }

    /**
     * For the daily check: true when a notification should go out now, and remembers that it
     * did, so the next one waits a whole interval.
     */
    suspend fun claimNotification(now: Long): Boolean {
        val settings = _settings.value
        val due = BackupReminder.isDue(
            settings = settings,
            lastExport = backup.observeLastExport().first(),
            countingSince = countingSince(),
            hasTransactions = transactions.observeAny().first(),
            now = now
        )
        val lastNotified = prefs.getLong(LAST_NOTIFIED, 0).takeIf { it > 0 }
        val notify = due && BackupReminder.shouldNotify(settings, lastNotified, now)
        if (notify) prefs.edit().putLong(LAST_NOTIFIED, now).apply()
        return notify
    }

    /** When the reminder started counting, before the first export: the first time it's read. */
    private fun countingSince(): Long {
        val saved = prefs.getLong(SINCE, 0)
        if (saved > 0) return saved
        val now = System.currentTimeMillis()
        prefs.edit().putLong(SINCE, now).apply()
        return now
    }

    private fun readSettings() = ReminderSettings(
        enabled = prefs.getBoolean(ENABLED, true),
        interval = ReminderInterval.entries.firstOrNull { it.days == prefs.getInt(DAYS, 0) }
            ?: ReminderInterval.MONTH
    )

    private fun save(settings: ReminderSettings) {
        prefs.edit()
            .putBoolean(ENABLED, settings.enabled)
            .putInt(DAYS, settings.interval.days)
            .apply()
        _settings.value = settings
    }

    private companion object {
        const val PREFS = "khata_backup_reminder"
        const val ENABLED = "enabled"
        const val DAYS = "interval_days"
        const val SINCE = "counting_since"
        const val LAST_NOTIFIED = "last_notified"
    }
}
