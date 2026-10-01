package com.openhand.khata.feature.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.openhand.khata.core.data.DuplicateMatch
import com.openhand.khata.sms.parser.FilterReason
import com.openhand.khata.sms.parser.NotTransactionGroup

// Which filter dropped an SMS, in words, for Test a message.

@Composable
internal fun filterReasonText(reason: FilterReason): String = when (reason) {
    FilterReason.PhoneNumber -> stringResource(R.string.filter_reason_phone_number)
    FilterReason.Promotional -> stringResource(R.string.filter_reason_promotional)
    FilterReason.Government -> stringResource(R.string.filter_reason_government)
    FilterReason.NotService -> stringResource(R.string.filter_reason_not_service)
    FilterReason.NoAmount -> stringResource(R.string.filter_reason_no_amount)
    FilterReason.NoTransactionWord -> stringResource(R.string.filter_reason_no_transaction_word)
    is FilterReason.IgnoredSender -> stringResource(R.string.filter_reason_ignored_sender)
    is FilterReason.IgnoredLikeThis -> stringResource(R.string.filter_reason_ignored_like_this)
    is FilterReason.NotTransaction -> stringResource(
        R.string.filter_reason_not_transaction,
        stringResource(groupLabel(reason.group)),
        reason.words.joinToString(", ") { "‘$it’" }
    )
}

/** Dropped before any text was read, rather than after no rule read it. */
internal val FilterReason.isSenderFilter: Boolean
    get() = this == FilterReason.PhoneNumber ||
        this == FilterReason.Promotional ||
        this == FilterReason.Government ||
        this == FilterReason.NotService

@StringRes
internal fun groupLabel(group: NotTransactionGroup): Int = when (group) {
    NotTransactionGroup.OTP -> R.string.filter_group_otp
    NotTransactionGroup.COLLECT_REQUEST -> R.string.filter_group_collect_request
    NotTransactionGroup.REMINDER -> R.string.filter_group_reminder
    NotTransactionGroup.BALANCE -> R.string.filter_group_balance
    NotTransactionGroup.MANDATE -> R.string.filter_group_mandate
    NotTransactionGroup.OFFER -> R.string.filter_group_offer
    NotTransactionGroup.FAILED -> R.string.filter_group_failed
}

@StringRes
internal fun duplicateLabel(match: DuplicateMatch): Int = when (match) {
    DuplicateMatch.REFERENCE -> R.string.test_sms_duplicate_reference
    DuplicateMatch.SAME_SMS -> R.string.test_sms_duplicate_same_sms
    DuplicateMatch.AMOUNT_AND_TIME -> R.string.test_sms_duplicate_amount_time
}
