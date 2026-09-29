package com.openhand.khata.sms.ingest

import com.openhand.khata.core.data.SmsImportResult
import com.openhand.khata.core.data.SmsImporter
import com.openhand.khata.core.model.SmsTransaction
import com.openhand.khata.sms.parser.BuiltInRules
import com.openhand.khata.sms.parser.ParseResult
import com.openhand.khata.sms.parser.ParsedSms
import com.openhand.khata.sms.parser.SmsParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one path every SMS takes, from the inbox import or (later) the new-SMS receiver: parse it
 * with the rules, and save a transaction through [SmsImporter].
 */
@Singleton
class SmsIngestor @Inject constructor(private val importer: SmsImporter) {
    // Loaded on first use, in the time zone the phone is in then.
    private val parser by lazy { SmsParser(BuiltInRules.load()) }

    /** Whether [sender] is a bank the rules know; anyone else's SMS isn't read further. */
    fun isBankSender(sender: String): Boolean = parser.isKnownSender(sender)

    suspend fun ingest(sender: String, body: String, receivedAt: Long): IngestOutcome =
        when (val result = parser.parse(sender, body, receivedAt)) {
            is ParseResult.Parsed -> save(result.sms, body)
            // Kept for the review inbox once it exists (#57); counted for now.
            is ParseResult.Unparsed -> IngestOutcome.UNREADABLE
            ParseResult.NotTransaction, ParseResult.UnknownSender -> IngestOutcome.IGNORED
        }

    private suspend fun save(sms: ParsedSms, body: String): IngestOutcome =
        when (val saved = importer.import(sms.toTransaction(body))) {
            is SmsImportResult.Duplicate -> IngestOutcome.ALREADY_THERE
            is SmsImportResult.Saved -> when {
                saved.needsReview -> IngestOutcome.RECORDED_FOR_REVIEW
                else -> IngestOutcome.RECORDED
            }
        }

    private fun ParsedSms.toTransaction(body: String) = SmsTransaction(
        amountPaise = amountPaise,
        direction = direction,
        timestamp = timestamp,
        bank = bank,
        accountType = accountType,
        accountLast4 = accountLast4,
        payee = payee,
        referenceNo = reference,
        rawSms = body
    )
}

enum class IngestOutcome {
    RECORDED,

    /** Saved, from a payee the user hasn't named yet. */
    RECORDED_FOR_REVIEW,
    ALREADY_THERE,

    /** From a bank, has an amount, but no rule could read it. */
    UNREADABLE,

    /** Not a transaction (an OTP, an offer…) or not from a bank. */
    IGNORED
}
