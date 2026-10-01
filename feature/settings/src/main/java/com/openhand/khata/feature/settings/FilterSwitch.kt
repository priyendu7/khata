package com.openhand.khata.feature.settings

import androidx.annotation.StringRes
import com.openhand.khata.sms.parser.FilterReason
import com.openhand.khata.sms.parser.SmsFilters

/** One switch on Settings > SMS import > Filters (PRD feature 7), and the [SmsFilters] field it sets. */
enum class FilterSwitch(
    @StringRes val title: Int,
    @StringRes val summary: Int,
    /** A sender filter, checked before any text is read; otherwise a content filter. */
    val beforeReading: Boolean,
    val isOn: (SmsFilters) -> Boolean,
    val set: (SmsFilters, Boolean) -> SmsFilters
) {
    PROMOTIONAL(
        R.string.filter_promotional,
        R.string.filter_promotional_note,
        beforeReading = true,
        isOn = { it.dropPromotional },
        set = { f, on -> f.copy(dropPromotional = on) }
    ),
    GOVERNMENT(
        R.string.filter_government,
        R.string.filter_government_note,
        beforeReading = true,
        isOn = { it.dropGovernment },
        set = { f, on -> f.copy(dropGovernment = on) }
    ),
    ONLY_SERVICE(
        R.string.filter_only_service,
        R.string.filter_only_service_note,
        beforeReading = true,
        isOn = { it.onlyService },
        set = { f, on -> f.copy(onlyService = on) }
    ),
    NO_AMOUNT(
        R.string.filter_no_amount,
        R.string.filter_no_amount_note,
        beforeReading = false,
        isOn = { it.noAmount },
        set = { f, on -> f.copy(noAmount = on) }
    ),
    NO_TRANSACTION_WORD(
        R.string.filter_no_transaction_word,
        R.string.filter_no_transaction_word_note,
        beforeReading = false,
        isOn = { it.noTransactionWord },
        set = { f, on -> f.copy(noTransactionWord = on) }
    ),
    NOT_TRANSACTION(
        R.string.filter_not_transaction,
        R.string.filter_not_transaction_note,
        beforeReading = false,
        isOn = { it.notTransaction },
        set = { f, on -> f.copy(notTransaction = on) }
    );

    companion object {
        /** The switch that dropped an SMS for [reason]; null for phone numbers and ignore rules. */
        fun of(reason: FilterReason): FilterSwitch? = when (reason) {
            FilterReason.PhoneNumber -> null
            FilterReason.Promotional -> PROMOTIONAL
            FilterReason.Government -> GOVERNMENT
            FilterReason.NotService -> ONLY_SERVICE
            FilterReason.NoAmount -> NO_AMOUNT
            FilterReason.NoTransactionWord -> NO_TRANSACTION_WORD
            is FilterReason.NotTransaction -> NOT_TRANSACTION
            // Ignore rules are listed below the switches, not switches themselves.
            is FilterReason.IgnoredSender, is FilterReason.IgnoredLikeThis -> null
        }
    }
}
