package com.openhand.khata.feature.settings

import com.openhand.khata.core.data.BackupReminderRepository
import com.openhand.khata.core.data.PreferencesData
import com.openhand.khata.core.data.SettingsFile
import com.openhand.khata.core.data.SmsFiltersData
import com.openhand.khata.core.model.ReminderInterval
import com.openhand.khata.core.security.lock.LockMethod
import com.openhand.khata.core.security.lock.LockSettings
import com.openhand.khata.core.security.lock.LockTimeout
import com.openhand.khata.core.security.lock.PinManager
import com.openhand.khata.sms.ingest.SmsImportSettings
import com.openhand.khata.sms.parser.SmsFilters
import javax.inject.Inject

/** The preferences a settings file carries. Null means "not in the file", so left as it is. */
data class AppPrefs(
    val smsFilters: SmsFilters? = null,
    val smsImport: Boolean? = null,
    val backupReminder: Boolean? = null,
    val reminderInterval: ReminderInterval? = null,
    val language: AppLanguage? = null,
    val appLock: Boolean? = null,
    val lockMethod: LockMethod? = null,
    val lockTimeout: LockTimeout? = null,
    val blockScreenshots: Boolean? = null
) {
    /** [file] with these preferences in it. */
    fun writeTo(file: SettingsFile): SettingsFile = file.copy(
        smsFilters = smsFilters?.let {
            SmsFiltersData(
                dropPromotional = it.dropPromotional,
                dropGovernment = it.dropGovernment,
                onlyService = it.onlyService,
                noAmount = it.noAmount,
                noTransactionWord = it.noTransactionWord,
                notTransaction = it.notTransaction
            )
        },
        preferences = PreferencesData(
            smsImport = smsImport,
            backupReminder = backupReminder,
            backupReminderDays = reminderInterval?.days,
            language = language?.let { if (it == AppLanguage.SYSTEM) SYSTEM_LANGUAGE else it.tag },
            appLock = appLock,
            lockMethod = lockMethod?.stored,
            lockTimeout = lockTimeout?.stored,
            blockScreenshots = blockScreenshots
        )
    )

    companion object {
        private const val SYSTEM_LANGUAGE = "system"

        /** Values this Khata doesn't know are left out, like ones the file doesn't have. */
        fun from(file: SettingsFile): AppPrefs {
            val prefs = file.preferences
            return AppPrefs(
                smsFilters = file.smsFilters?.let {
                    SmsFilters(
                        dropPromotional = it.dropPromotional,
                        dropGovernment = it.dropGovernment,
                        onlyService = it.onlyService,
                        noAmount = it.noAmount,
                        noTransactionWord = it.noTransactionWord,
                        notTransaction = it.notTransaction
                    )
                },
                smsImport = prefs.smsImport,
                backupReminder = prefs.backupReminder,
                reminderInterval = ReminderInterval.entries
                    .firstOrNull { it.days == prefs.backupReminderDays },
                language = prefs.language?.let { tag ->
                    AppLanguage.entries.firstOrNull {
                        if (it == AppLanguage.SYSTEM) tag == SYSTEM_LANGUAGE else it.tag == tag
                    }
                },
                appLock = prefs.appLock,
                lockMethod = LockMethod.entries.firstOrNull { it.stored == prefs.lockMethod },
                lockTimeout = LockTimeout.entries.firstOrNull { it.stored == prefs.lockTimeout },
                blockScreenshots = prefs.blockScreenshots
            )
        }
    }
}

/** What applying preferences left for the user to do. */
data class PrefsOutcome(
    /** SMS import was on in the file but is off here; only the user can turn it on. */
    val offerSmsImport: Boolean = false,
    /** The file uses an app PIN and this phone has none, so the phone's lock is used for now. */
    val needsPin: Boolean = false
)

/**
 * Reads and applies the preferences kept outside the database: SMS filters, the backup reminder,
 * the app lock (never its PIN or recovery code) and the language.
 */
class AppPreferences @Inject constructor(
    private val sms: SmsImportSettings,
    private val reminder: BackupReminderRepository,
    private val lock: LockSettings,
    private val pins: PinManager
) {
    fun current(): AppPrefs = AppPrefs(
        smsFilters = sms.filters.value,
        smsImport = sms.enabled.value,
        backupReminder = reminder.settings.value.enabled,
        reminderInterval = reminder.settings.value.interval,
        language = AppLanguage.current(),
        appLock = lock.enabled,
        lockMethod = lock.method,
        lockTimeout = lock.timeout,
        blockScreenshots = lock.blockScreenshots
    )

    /**
     * Applies everything but SMS import, which needs the permission, and the language, which
     * restarts the screen: see [applyLanguage].
     */
    fun apply(prefs: AppPrefs): PrefsOutcome {
        prefs.smsFilters?.let(sms::setFilters)
        prefs.backupReminder?.let(reminder::setEnabled)
        prefs.reminderInterval?.let(reminder::setInterval)
        prefs.appLock?.let { lock.enabled = it }
        prefs.lockTimeout?.let { lock.timeout = it }
        prefs.blockScreenshots?.let { lock.blockScreenshots = it }
        // Without a PIN here, switching to it would lock the user out.
        val needsPin = prefs.lockMethod == LockMethod.PIN && !pins.hasPin
        prefs.lockMethod?.let { lock.method = if (needsPin) LockMethod.DEVICE else it }
        return PrefsOutcome(
            offerSmsImport = prefs.smsImport == true && !sms.enabled.value,
            needsPin = needsPin
        )
    }

    /** Switches the language if the file has another one; open screens are recreated in it. */
    fun applyLanguage(prefs: AppPrefs) {
        prefs.language?.takeIf { it != AppLanguage.current() }?.applyToApp()
    }
}
