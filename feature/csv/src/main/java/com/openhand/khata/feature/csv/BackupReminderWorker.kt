package com.openhand.khata.feature.csv

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.openhand.khata.core.data.BackupReminderRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The daily check (PRD feature 6): posts the backup reminder when it's due. The notification
 * says only that it's time to back up, never any amounts or transactions.
 */
class BackupReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    /** Hilt can't construct workers without a custom factory, so the worker asks for this. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun reminders(): BackupReminderRepository
    }

    override suspend fun doWork(): Result {
        val reminders = EntryPointAccessors
            .fromApplication(applicationContext, Dependencies::class.java)
            .reminders()
        if (reminders.claimNotification(System.currentTimeMillis())) {
            BackupReminderNotifier(applicationContext).show(reminders.settings.value.interval.days)
        }
        return Result.success()
    }
}
