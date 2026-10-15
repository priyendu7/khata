package com.openhand.khata

import android.app.Application
import com.openhand.khata.core.data.EventRepository
import com.openhand.khata.feature.csv.BackupReminderScheduler
import com.openhand.khata.feature.insights.CardDueScheduler
import com.openhand.khata.feature.lock.AppLockLifecycle
import com.openhand.khata.sms.ingest.SmsImportSettings
import com.openhand.khata.sms.ingest.TransferBackfillWork
import dagger.hilt.android.HiltAndroidApp
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class KhataApp : Application() {
    @Inject lateinit var appLockLifecycle: AppLockLifecycle

    @Inject lateinit var backupReminderScheduler: BackupReminderScheduler

    @Inject lateinit var cardDueScheduler: CardDueScheduler

    @Inject lateinit var smsImportSettings: SmsImportSettings

    @Inject lateinit var transferBackfillWork: TransferBackfillWork

    @Inject lateinit var events: EventRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appLockLifecycle.start()
        backupReminderScheduler.start()
        cardDueScheduler.start()
        smsImportSettings.syncReceiver()
        transferBackfillWork.start()
        // Once, after the #144 update: gives back event tags that reviewing took off.
        scope.launch { events.repairTags(ZoneId.systemDefault()) }
    }
}
