package com.openhand.khata

import android.app.Application
import com.openhand.khata.feature.csv.BackupReminderScheduler
import com.openhand.khata.feature.lock.AppLockLifecycle
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KhataApp : Application() {
    @Inject lateinit var appLockLifecycle: AppLockLifecycle

    @Inject lateinit var backupReminderScheduler: BackupReminderScheduler

    override fun onCreate() {
        super.onCreate()
        appLockLifecycle.start()
        backupReminderScheduler.start()
    }
}
