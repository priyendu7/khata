package com.openhand.khata.sms.ingest

import com.openhand.khata.core.data.IgnoreRuleRepository
import com.openhand.khata.core.data.UnparsedSmsRepository
import com.openhand.khata.core.model.IgnoreKind
import com.openhand.khata.core.model.UnparsedSms
import com.openhand.khata.sms.parser.IgnoreTemplate
import com.openhand.khata.sms.parser.RuleMaker
import com.openhand.khata.sms.parser.SenderId
import javax.inject.Inject

/**
 * "Ignore this sender" and "ignore messages like this" from To review (PRD feature 7). Saving a
 * rule also removes the waiting SMS it covers, so the same kind of message isn't asked about
 * again. New SMS are dropped by the ingestor, which rebuilds its parser when the rules change.
 */
class SmsIgnoring @Inject constructor(
    private val rules: IgnoreRuleRepository,
    private val unparsed: UnparsedSmsRepository
) {
    /** `HDFCBK` for `VM-HDFCBK-S`. Null for anything that isn't a business sender. */
    fun headerOf(sender: String): String? = SenderId.parse(sender)?.header

    /** Ignores every SMS from [sms]'s sender. Returns how many waiting SMS were removed. */
    suspend fun ignoreSender(sms: UnparsedSms, now: Long = System.currentTimeMillis()): Int {
        val header = headerOf(sms.sender) ?: return 0
        rules.add(IgnoreKind.SENDER, header, pattern = null, sample = sms.body, now = now)
        return removeWaiting { headerOf(it.sender) == header }
    }

    /** The "like this" pattern for [sms], with the [changing] words as parts that change. */
    fun template(sms: UnparsedSms, changing: Collection<RuleMaker.Word>): String =
        IgnoreTemplate.pattern(sms.body, changing)

    /** How many SMS waiting in To review from [sms]'s sender [pattern] matches, [sms] included. */
    suspend fun waitingLike(sms: UnparsedSms, pattern: String): Int {
        val matches = matcher(sms, pattern) ?: return 0
        return unparsed.getAll().count(matches)
    }

    /** Ignores messages like [sms]. Returns how many waiting SMS were removed. */
    suspend fun ignoreLikeThis(
        sms: UnparsedSms,
        pattern: String,
        now: Long = System.currentTimeMillis()
    ): Int {
        val matches = matcher(sms, pattern) ?: return 0
        rules.add(IgnoreKind.TEMPLATE, headerOf(sms.sender)!!, pattern, sms.body, now)
        return removeWaiting(matches)
    }

    /** Whether a waiting SMS is from [sms]'s sender and matches [pattern]; null if it can't run. */
    private fun matcher(sms: UnparsedSms, pattern: String): ((UnparsedSms) -> Boolean)? {
        val header = headerOf(sms.sender)
        val compiled = IgnoreTemplate.compile(pattern)
        return if (header == null || compiled == null) {
            null
        } else {
            { headerOf(it.sender) == header && compiled.matches(it.body) }
        }
    }

    private suspend fun removeWaiting(matches: (UnparsedSms) -> Boolean): Int =
        unparsed.getAll().filter(matches).onEach { unparsed.delete(it.id) }.size
}
