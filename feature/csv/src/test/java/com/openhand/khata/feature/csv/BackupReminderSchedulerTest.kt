package com.openhand.khata.feature.csv

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.openhand.khata.core.data.BackupReminderRepository
import com.openhand.khata.core.data.BackupRepository
import com.openhand.khata.core.data.TransactionRepository
import com.openhand.khata.core.database.DefaultCategorySeeder
import com.openhand.khata.core.database.KhataDatabase
import dagger.Lazy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The daily check is scheduled while the reminder is on and cancelled when it's turned off. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupReminderSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: KhataDatabase
    private lateinit var workManager: WorkManager
    private lateinit var reminders: BackupReminderRepository
    private lateinit var scheduler: BackupReminderScheduler

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java)
            .addCallback(DefaultCategorySeeder.callback)
            .allowMainThreadQueries()
            .build()
        val lazyDb = Lazy { db }
        reminders = BackupReminderRepository(
            context,
            BackupRepository(lazyDb),
            TransactionRepository(lazyDb)
        )
        scheduler = BackupReminderScheduler(reminders) { workManager }
    }

    @After
    fun tearDown() = db.close()

    private fun state(): WorkInfo.State? = workManager
        .getWorkInfosForUniqueWork(BackupReminderScheduler.WORK_NAME).get()
        .singleOrNull()?.state

    @Test
    fun scheduledWhileOnAndCancelledWhenOff() {
        scheduler.apply(reminders.settings.value)
        assertEquals(WorkInfo.State.ENQUEUED, state())
        val info = workManager.getWorkInfosForUniqueWork(BackupReminderScheduler.WORK_NAME).get()
        assertEquals(true, info.single().periodicityInfo?.repeatIntervalMillis == DAY_MILLIS)

        reminders.setEnabled(false)
        scheduler.apply(reminders.settings.value)
        assertEquals(WorkInfo.State.CANCELLED, state())

        reminders.setEnabled(true)
        scheduler.apply(reminders.settings.value)
        assertEquals(WorkInfo.State.ENQUEUED, state())
    }

    @Test
    fun reapplyingKeepsTheSameJob() {
        scheduler.apply(reminders.settings.value)
        val first = workManager.getWorkInfosForUniqueWork(BackupReminderScheduler.WORK_NAME).get()
            .single().id
        scheduler.apply(reminders.settings.value)
        val second = workManager.getWorkInfosForUniqueWork(BackupReminderScheduler.WORK_NAME).get()
            .single().id
        assertEquals(first, second)
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
