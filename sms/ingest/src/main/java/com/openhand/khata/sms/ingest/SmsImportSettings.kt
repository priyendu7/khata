package com.openhand.khata.sms.ingest

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether SMS import is on, and what the last inbox import found. Kept in app-private
 * SharedPreferences, not the encrypted database, so the SMS receiver can check it without opening
 * the database; it holds only a switch and counts, no SMS or transactions.
 */
@Singleton
class SmsImportSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(ENABLED, false))
    private val _lastScan = MutableStateFlow(readSummary())

    /** Off until the user turns it on (PRD principle 6). The SMS permission is checked separately. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    val lastScan: StateFlow<ScanSummary?> = _lastScan.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    fun saveSummary(summary: ScanSummary) {
        prefs.edit()
            .putLong(SINCE, summary.since)
            .putLong(FINISHED, summary.finishedAt)
            .putInt(RECORDED, summary.recorded)
            .putInt(TO_REVIEW, summary.toReview)
            .putInt(ALREADY_THERE, summary.alreadyThere)
            .putInt(UNREADABLE, summary.unreadable)
            .apply()
        _lastScan.value = summary
    }

    private fun readSummary(): ScanSummary? = prefs.getLong(FINISHED, 0).takeIf { it > 0 }?.let {
        ScanSummary(
            since = prefs.getLong(SINCE, 0),
            finishedAt = it,
            recorded = prefs.getInt(RECORDED, 0),
            toReview = prefs.getInt(TO_REVIEW, 0),
            alreadyThere = prefs.getInt(ALREADY_THERE, 0),
            unreadable = prefs.getInt(UNREADABLE, 0)
        )
    }

    private companion object {
        const val PREFS = "khata_sms_import"
        const val ENABLED = "enabled"
        const val SINCE = "scan_since"
        const val FINISHED = "scan_finished"
        const val RECORDED = "scan_recorded"
        const val TO_REVIEW = "scan_to_review"
        const val ALREADY_THERE = "scan_already_there"
        const val UNREADABLE = "scan_unreadable"
    }
}

/** What one inbox import did. */
data class ScanSummary(
    /** The start date the user picked, epoch millis. */
    val since: Long,
    val finishedAt: Long,
    /** New transactions saved, including those waiting for review. */
    val recorded: Int,
    /** Of [recorded], those from payees the user hasn't named yet. */
    val toReview: Int,
    val alreadyThere: Int,
    /** Bank SMS with an amount that no rule could read. */
    val unreadable: Int
)
