package com.openhand.khata.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Is a reminder due", with a fixed clock. */
class BackupReminderTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_790_000_000_000
    private val on = ReminderSettings()

    private fun due(
        settings: ReminderSettings = on,
        lastExport: Long? = now - 30 * day,
        since: Long = now - 100 * day,
        hasTransactions: Boolean = true
    ) = BackupReminder.isDue(settings, lastExport, since, hasTransactions, now)

    @Test
    fun dueOnceTheIntervalHasPassedSinceTheLastExport() {
        assertTrue(due(lastExport = now - 30 * day))
        assertTrue(due(lastExport = now - 45 * day))
        assertFalse(due(lastExport = now - 30 * day + 1))
        assertFalse(due(lastExport = now))
    }

    @Test
    fun followsTheChosenInterval() {
        val week = on.copy(interval = ReminderInterval.WEEK)
        assertTrue(due(week, lastExport = now - 7 * day))
        assertFalse(due(week, lastExport = now - 6 * day))
        val twoMonths = on.copy(interval = ReminderInterval.TWO_MONTHS)
        assertFalse(due(twoMonths, lastExport = now - 59 * day))
        assertTrue(due(twoMonths, lastExport = now - 60 * day))
    }

    @Test
    fun beforeTheFirstExportItCountsFromWhenTheReminderStarted() {
        assertFalse(due(lastExport = null, since = now - 29 * day))
        assertTrue(due(lastExport = null, since = now - 30 * day))
    }

    @Test
    fun neverWhenOffOrWithNothingToBackUp() {
        assertFalse(due(settings = on.copy(enabled = false), lastExport = now - 365 * day))
        assertFalse(due(hasTransactions = false, lastExport = null, since = now - 365 * day))
    }

    @Test
    fun notifiesAtMostOncePerInterval() {
        assertTrue(BackupReminder.shouldNotify(on, lastNotified = null, now = now))
        assertFalse(BackupReminder.shouldNotify(on, lastNotified = now - day, now = now))
        assertTrue(BackupReminder.shouldNotify(on, lastNotified = now - 30 * day, now = now))
    }
}
