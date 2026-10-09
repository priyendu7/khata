package com.openhand.khata.feature.settings

import com.openhand.khata.core.data.PreferencesData
import com.openhand.khata.core.data.SettingsFile
import com.openhand.khata.core.model.ReminderInterval
import com.openhand.khata.core.security.lock.LockMethod
import com.openhand.khata.core.security.lock.LockTimeout
import com.openhand.khata.sms.parser.SmsFilters
import org.junit.Assert.assertEquals
import org.junit.Test

class AppPrefsTest {
    @Test
    fun roundTripsThroughTheFile() {
        val prefs = AppPrefs(
            smsFilters = SmsFilters(dropPromotional = false, noAmount = false),
            smsImport = true,
            backupReminder = false,
            reminderInterval = ReminderInterval.TWO_WEEKS,
            language = AppLanguage.HINDI,
            appLock = true,
            lockMethod = LockMethod.PIN,
            lockTimeout = LockTimeout.MINUTES_5,
            blockScreenshots = false
        )

        assertEquals(prefs, AppPrefs.from(prefs.writeTo(SettingsFile())))
        assertEquals(
            AppLanguage.SYSTEM,
            AppPrefs.from(AppPrefs(language = AppLanguage.SYSTEM).writeTo(SettingsFile())).language
        )
    }

    @Test
    fun valuesThisVersionDoesNotKnowAreLeftAlone() {
        val file = SettingsFile(
            preferences = PreferencesData(
                backupReminderDays = 45,
                language = "fr",
                lockMethod = "face",
                lockTimeout = "1h"
            )
        )

        assertEquals(AppPrefs(), AppPrefs.from(file))
    }
}
