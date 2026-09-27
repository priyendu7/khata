package com.openhand.khata.core.model

/** How long without a CSV export before Khata reminds the user to back up (PRD feature 6). */
enum class ReminderInterval(val days: Int) {
    WEEK(7),
    TWO_WEEKS(14),
    MONTH(30),
    TWO_MONTHS(60);

    val millis: Long get() = days * MILLIS_PER_DAY

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}

/** The backup reminder setting. On, every 30 days, unless the user changes it. */
data class ReminderSettings(
    val enabled: Boolean = true,
    val interval: ReminderInterval = ReminderInterval.MONTH
)

/**
 * When to remind the user to back up. Android backup is off (PRD privacy principle 5), so a CSV
 * export is the only backup, and losing the phone loses everything since the last one.
 */
object BackupReminder {
    /**
     * Whether it's time to remind: the reminder is on, there's something to back up, and the
     * interval has passed since the last export. Before the first export, it counts from
     * [countingSince] (when the reminder first started), so a new user isn't nagged on day one.
     */
    fun isDue(
        settings: ReminderSettings,
        lastExport: Long?,
        countingSince: Long,
        hasTransactions: Boolean,
        now: Long
    ): Boolean = settings.enabled &&
        hasTransactions &&
        now - (lastExport ?: countingSince) >= settings.interval.millis

    /**
     * Whether to post a notification for a due reminder: at most once per interval, so a user
     * who doesn't export yet isn't notified every day. The Home banner stays until they do.
     */
    fun shouldNotify(settings: ReminderSettings, lastNotified: Long?, now: Long): Boolean =
        lastNotified == null || now - lastNotified >= settings.interval.millis
}
