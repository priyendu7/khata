package com.openhand.khata.feature.csv

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.openhand.khata.core.data.BackupReminderRepository
import com.openhand.khata.core.model.ReminderSettings
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Keeps the daily reminder check scheduled while the reminder is on, and cancelled while off. */
class BackupReminderScheduler @Inject constructor(
    private val reminders: BackupReminderRepository,
    private val workManager: dagger.Lazy<WorkManager>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Call once from `Application.onCreate`; follows the setting from then on. */
    fun start() {
        scope.launch { reminders.settings.collect(::apply) }
    }

    fun apply(settings: ReminderSettings) {
        if (settings.enabled) {
            val check = PeriodicWorkRequestBuilder<BackupReminderWorker>(1, TimeUnit.DAYS).build()
            // KEEP: reopening the app mustn't push the next check back by a day each time.
            workManager.get()
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, check)
        } else {
            workManager.get().cancelUniqueWork(WORK_NAME)
        }
    }

    companion object {
        const val WORK_NAME = "backup_reminder"
    }
}
