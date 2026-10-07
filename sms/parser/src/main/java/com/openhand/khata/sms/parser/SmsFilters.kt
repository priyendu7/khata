package com.openhand.khata.sms.parser

import com.openhand.khata.core.model.SenderCategory
import com.openhand.khata.core.model.SenderId

/**
 * The SMS filters (PRD feature 7), each a switch the user can turn off; all are on by default.
 *
 * Sender filters run first, before any text is read, and apply to every sender, including ones
 * that have a rule. Phone numbers are never read, whatever the switches say. Content filters run
 * only on an SMS that no rule read, so a rule that reads an SMS always wins.
 */
data class SmsFilters(
    val dropPromotional: Boolean = true,
    val dropGovernment: Boolean = true,
    /** Drops `-T`, `-P` and `-G` senders. A sender shown with no suffix passes. */
    val onlyService: Boolean = true,
    val noAmount: Boolean = true,
    val noTransactionWord: Boolean = true,
    /** OTPs, collect requests, reminders, balance-only SMS, mandates, offers, failed payments. */
    val notTransaction: Boolean = true
) {
    /** Why [sender] is dropped before its text is read, or null if it's read. */
    fun senderReason(sender: String): FilterReason? {
        val category = (SenderId.parse(sender) ?: return FilterReason.PhoneNumber).category
        return when {
            dropPromotional && category == SenderCategory.PROMOTIONAL -> FilterReason.Promotional
            dropGovernment && category == SenderCategory.GOVERNMENT -> FilterReason.Government
            onlyService && category != null && category != SenderCategory.SERVICE ->
                FilterReason.NotService
            else -> null
        }
    }

    /**
     * Why an SMS no rule read is dropped, or null if it goes to review. A matched word group is
     * checked first, as it says the most about the SMS.
     */
    fun contentReason(body: String): FilterReason? {
        val group = if (notTransaction) NotTransactionFilter.notTransaction(body) else null
        return when {
            group != null -> group
            noAmount && !NotTransactionFilter.hasAmount(body) -> FilterReason.NoAmount
            noTransactionWord && !TransactionWords.found(body) -> FilterReason.NoTransactionWord
            else -> null
        }
    }
}

/** Which filter dropped an SMS. */
sealed interface FilterReason {
    /** A phone number or anything else that isn't a business sender ID. Not a switch. */
    data object PhoneNumber : FilterReason

    data object Promotional : FilterReason

    data object Government : FilterReason

    /** A `-T` sender (or `-P`/`-G` with their own switches off) under "only service senders". */
    data object NotService : FilterReason

    data object NoAmount : FilterReason

    data object NoTransactionWord : FilterReason

    /** A sender the user chose to ignore, by ignore rule [ruleId]. Checked before reading. */
    data class IgnoredSender(val ruleId: Long) : FilterReason

    /** Matched the user's "ignore messages like this" rule [ruleId]. */
    data class IgnoredLikeThis(val ruleId: Long) : FilterReason

    /** The SMS matched [group]'s [words] (as written in the SMS). */
    data class NotTransaction(val group: NotTransactionGroup, val words: List<String>) :
        FilterReason
}

/** The kinds of SMS the "not a transaction" filter recognises. */
enum class NotTransactionGroup {
    OTP,

    /** A UPI collect request: money was asked for, not moved. */
    COLLECT_REQUEST,

    /** A bill, card or loan reminder. */
    REMINDER,
    BALANCE,

    /** A mandate or standing instruction being set up. */
    MANDATE,
    OFFER,

    /** A failed payment that no money came back for. */
    FAILED
}
