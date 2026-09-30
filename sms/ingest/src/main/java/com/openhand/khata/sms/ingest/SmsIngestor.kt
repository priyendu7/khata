package com.openhand.khata.sms.ingest

import com.openhand.khata.core.data.CustomParserRepository
import com.openhand.khata.core.data.SmsImportResult
import com.openhand.khata.core.data.SmsImporter
import com.openhand.khata.core.data.UnparsedSmsRepository
import com.openhand.khata.core.model.SmsTransaction
import com.openhand.khata.sms.parser.BuiltInRules
import com.openhand.khata.sms.parser.CompiledRule
import com.openhand.khata.sms.parser.CustomRules
import com.openhand.khata.sms.parser.ParseResult
import com.openhand.khata.sms.parser.ParsedSms
import com.openhand.khata.sms.parser.SmsParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one path every SMS takes, from the inbox import or the new-SMS receiver: parse it with the
 * rules, and save a transaction through [SmsImporter].
 */
@Singleton
class SmsIngestor @Inject constructor(
    private val importer: SmsImporter,
    private val unparsed: UnparsedSmsRepository,
    private val customParsers: CustomParserRepository
) {
    private val builtIn by lazy { BuiltInRules.load() }

    /** The last parser built, with the custom rule codes it was built from. */
    @Volatile private var current: Pair<List<String>, SmsParser>? = null

    /**
     * The rules as they are now: the custom rules that are switched on (PRD feature 8), then the
     * built-in ones. Rebuilt only when the custom rules change, in the phone's time zone then.
     */
    suspend fun parser(): SmsParser {
        val codes = customParsers.enabledCodes()
        current?.let { (builtFrom, parser) -> if (builtFrom == codes) return parser }
        return CustomRules.parser(CustomRules.load(codes), builtIn).also { current = codes to it }
    }

    /** Whether [sender] is a bank the rules know; anyone else's SMS isn't read further. */
    suspend fun isBankSender(sender: String): Boolean = parser().isKnownSender(sender)

    suspend fun ingest(sender: String, body: String, receivedAt: Long): IngestOutcome =
        ingest(sender, body, receivedAt, parser())

    /** With a [parser] fetched once, for many SMS in a row. */
    internal suspend fun ingest(
        sender: String,
        body: String,
        receivedAt: Long,
        parser: SmsParser
    ): IngestOutcome = when (val result = parser.parse(sender, body, receivedAt)) {
        is ParseResult.Parsed -> save(result.sms, body)
        // Kept with its raw text for the review inbox, so a new format is noticed.
        is ParseResult.Unparsed ->
            if (unparsed.save(sender, body, receivedAt)) {
                IngestOutcome.UNREADABLE
            } else {
                IngestOutcome.ALREADY_THERE
            }
        ParseResult.NotTransaction, ParseResult.UnknownSender -> IngestOutcome.IGNORED
    }

    /** How many of the SMS waiting in To review [rule] can read, before it's used on them. */
    suspend fun unparsedReadableBy(rule: CompiledRule): Int {
        val parser = SmsParser(listOf(rule))
        return unparsed.getAll().count {
            parser.parse(it.sender, it.body, it.receivedAt) is ParseResult.Parsed
        }
    }

    /**
     * Reads the SMS waiting in To review again with the current rules, after one was added. Each
     * one a rule now reads becomes a transaction (or is found to be one already saved) and leaves
     * To review. Returns how many did.
     */
    suspend fun retryUnparsed(): Int {
        val parser = parser()
        return unparsed.getAll().count { sms ->
            val result = parser.parse(sms.sender, sms.body, sms.receivedAt)
            if (result is ParseResult.Parsed) {
                save(result.sms, sms.body)
                unparsed.delete(sms.id)
            }
            result is ParseResult.Parsed
        }
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
