package com.openhand.khata.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.openhand.khata.core.model.ReminderInterval
import com.openhand.khata.core.model.ReminderSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupReminderRepositoryTest : RepositoryTest() {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val backup by lazy { BackupRepository(lazyDb) }
    private val day = 24L * 60 * 60 * 1000
    private val now = System.currentTimeMillis()

    private fun reminders() =
        BackupReminderRepository(context, backup, TransactionRepository(lazyDb))

    @Test
    fun onEveryThirtyDaysByDefaultAndSavesChanges() {
        assertEquals(ReminderSettings(true, ReminderInterval.MONTH), reminders().settings.value)
        reminders().setInterval(ReminderInterval.TWO_WEEKS)
        reminders().setEnabled(false)
        // A new instance reads what was saved, as after an app restart.
        assertEquals(
            ReminderSettings(false, ReminderInterval.TWO_WEEKS),
            reminders().settings.value
        )
    }

    @Test
    fun dueAfterTheIntervalWithoutAnExport() = runTest {
        val reminders = reminders()
        addTransaction()
        backup.markExported(now - 31 * day)
        assertTrue(reminders.observeDue { now }.first())

        backup.markExported(now)
        assertFalse(reminders.observeDue { now }.first())
        assertTrue(reminders.observeDue { now + 30 * day }.first())

        reminders.setEnabled(false)
        assertFalse(reminders.observeDue { now + 30 * day }.first())
    }

    @Test
    fun aNewUserIsNotRemindedStraightAway() = runTest {
        val reminders = reminders()
        addTransaction()
        assertFalse(reminders.observeDue { now }.first())
        assertTrue(reminders.observeDue { now + 31 * day }.first())
    }

    @Test
    fun notifiesOncePerInterval() = runTest {
        val reminders = reminders()
        addTransaction()
        backup.markExported(now - 40 * day)
        assertTrue(reminders.claimNotification(now))
        assertFalse(reminders.claimNotification(now + day))
        assertTrue(reminders.claimNotification(now + 30 * day))
    }

    @Test
    fun nothingToBackUpMeansNoReminder() = runTest {
        backup.markExported(now - 400 * day)
        assertFalse(reminders().claimNotification(now))
    }
}
