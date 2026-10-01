package com.openhand.khata.sms.ingest

import android.content.Context
import com.openhand.khata.sms.parser.SmsFilters
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether SMS import is on, which filters are on, and what the last inbox import found. Kept in
 * app-private SharedPreferences, not the encrypted database, so the SMS receiver can check it
 * without opening the database; it holds only switches and counts, no SMS or transactions.
 */
@Singleton
class SmsImportSettings @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(ENABLED, false))
    private val _lastScan = MutableStateFlow(readSummary())
    private val _filters = MutableStateFlow(readFilters())

    /** Off until the user turns it on (PRD principle 6). The SMS permission is checked separately. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    val lastScan: StateFlow<ScanSummary?> = _lastScan.asStateFlow()

    /** The SMS filters (PRD feature 7), all on until the user turns one off. */
    val filters: StateFlow<SmsFilters> = _filters.asStateFlow()

    /**
     * Brings the new-SMS receiver in line with the switch. Call at app start: the receiver starts
     * disabled in the manifest, and may be out of step after an update.
     */
    fun syncReceiver() = SmsReceiver.setEnabled(context, _enabled.value)

    /** Also turns the new-SMS receiver on or off. */
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(ENABLED, enabled).apply()
        _enabled.value = enabled
        SmsReceiver.setEnabled(context, enabled)
    }

    fun setFilters(filters: SmsFilters) {
        prefs.edit()
            .putBoolean(DROP_PROMOTIONAL, filters.dropPromotional)
            .putBoolean(DROP_GOVERNMENT, filters.dropGovernment)
            .putBoolean(ONLY_SERVICE, filters.onlyService)
            .putBoolean(NO_AMOUNT, filters.noAmount)
            .putBoolean(NO_TRANSACTION_WORD, filters.noTransactionWord)
            .putBoolean(NOT_TRANSACTION, filters.notTransaction)
            .apply()
        _filters.value = filters
    }

    fun saveSummary(summary: ScanSummary) {
        prefs.edit()
            .putLong(SINCE, summary.since)
            .putLong(FINISHED, summary.finishedAt)
            .putInt(RECORDED, summary.recorded)
            .putInt(TO_REVIEW, summary.toReview)
            .putInt(ALREADY_THERE, summary.alreadyThere)
            .putInt(UNREADABLE, summary.unreadable)
            .putInt(FILTERED, summary.filtered)
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
            unreadable = prefs.getInt(UNREADABLE, 0),
            filtered = prefs.getInt(FILTERED, 0)
        )
    }

    private fun readFilters() = SmsFilters(
        dropPromotional = prefs.getBoolean(DROP_PROMOTIONAL, true),
        dropGovernment = prefs.getBoolean(DROP_GOVERNMENT, true),
        onlyService = prefs.getBoolean(ONLY_SERVICE, true),
        noAmount = prefs.getBoolean(NO_AMOUNT, true),
        noTransactionWord = prefs.getBoolean(NO_TRANSACTION_WORD, true),
        notTransaction = prefs.getBoolean(NOT_TRANSACTION, true)
    )

    private companion object {
        const val PREFS = "khata_sms_import"
        const val ENABLED = "enabled"
        const val SINCE = "scan_since"
        const val FINISHED = "scan_finished"
        const val RECORDED = "scan_recorded"
        const val TO_REVIEW = "scan_to_review"
        const val ALREADY_THERE = "scan_already_there"
        const val UNREADABLE = "scan_unreadable"
        const val FILTERED = "scan_filtered"
        const val DROP_PROMOTIONAL = "filter_drop_promotional"
        const val DROP_GOVERNMENT = "filter_drop_government"
        const val ONLY_SERVICE = "filter_only_service"
        const val NO_AMOUNT = "filter_no_amount"
        const val NO_TRANSACTION_WORD = "filter_no_transaction_word"
        const val NOT_TRANSACTION = "filter_not_transaction"
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
    /** SMS that got past the filters but no rule could read. */
    val unreadable: Int,
    /** SMS a filter dropped. 0 for an import from before filters had a count. */
    val filtered: Int = 0
)
