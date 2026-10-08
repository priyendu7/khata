package com.openhand.khata.sms.ingest

import com.openhand.khata.core.data.BuiltInRuleOverrideRepository
import com.openhand.khata.core.data.CustomParserRepository
import com.openhand.khata.core.data.IgnoreRuleRepository
import com.openhand.khata.core.data.SmsImportPreview
import com.openhand.khata.core.data.SmsImportResult
import com.openhand.khata.core.data.SmsImporter
import com.openhand.khata.core.data.UnparsedSmsRepository
import com.openhand.khata.core.model.SmsTransaction
import com.openhand.khata.sms.parser.BuiltInRules
import com.openhand.khata.sms.parser.CompiledRule
import com.openhand.khata.sms.parser.CustomRules
import com.openhand.khata.sms.parser.IgnoreRule
import com.openhand.khata.sms.parser.IgnoreRules
import com.openhand.khata.sms.parser.ParseResult
import com.openhand.khata.sms.parser.ParsedSms
import com.openhand.khata.sms.parser.RuleOverride
import com.openhand.khata.sms.parser.SmsFilters
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
    private val customParsers: CustomParserRepository,
    private val builtInOverrides: BuiltInRuleOverrideRepository,
    private val ignoreRules: IgnoreRuleRepository,
    private val settings: SmsImportSettings
) {
    private val builtIn by lazy { BuiltInRules.load() }

    /** The last parser built, with what it was built from and its custom rule ids. */
    @Volatile private var current: Built? = null

    /** What the parser is built from; it's rebuilt when any of this changes. */
    private data class Inputs(
        val codes: List<String>,
        val overrides: List<RuleOverride>,
        val filters: SmsFilters,
        val ignore: List<IgnoreRule>
    )

    private class Built(val inputs: Inputs, val parser: SmsParser, val customIds: Set<String>)

    /**
     * The rules as they are now: the custom rules that are switched on (PRD feature 8), then the
     * built-in ones with the user's changes (switched off or edited, #111), behind the filters
     * and ignore rules the user has on. Rebuilt only when one of those changes, in the phone's
     * time zone then.
     */
    suspend fun parser(): SmsParser = built().parser

    private suspend fun built(): Built {
        val inputs = Inputs(
            codes = customParsers.enabledCodes(),
            overrides = builtInOverrides.getAll().map {
                RuleOverride(it.ruleId, it.enabled, it.editedCode)
            },
            filters = settings.filters.value,
            ignore = ignoreRules.enabled().map { IgnoreRule(it.id, it.header, it.pattern) }
        )
        current?.let { if (it.inputs == inputs) return it }
        val custom = CustomRules.load(inputs.codes)
        val builtInNow = BuiltInRules.withOverrides(builtIn, inputs.overrides)
        val parser = CustomRules.parser(
            custom,
            builtInNow,
            inputs.filters,
            IgnoreRules(inputs.ignore)
        )
        return Built(inputs, parser, custom.mapTo(mutableSetOf()) { it.rule.id })
            .also { current = it }
    }

    /**
     * What [ingest] would do with this SMS, saving nothing (Settings > SMS import > Test a
     * message). It takes the same steps with the same parser, read-only.
     */
    suspend fun explain(sender: String, body: String, receivedAt: Long): SmsExplanation {
        val built = built()
        val explanation = built.parser.explain(sender, body, receivedAt)
        val preview = (explanation.result as? ParseResult.Parsed)
            ?.let { importer.preview(it.sms.toTransaction(body)) }
        return SmsExplanation(
            result = explanation.result,
            rulesTried = explanation.rulesTried.map {
                TriedRule(it, custom = it in built.customIds)
            },
            preview = preview
        )
    }

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
        is ParseResult.Filtered -> IngestOutcome.FILTERED
    }

    /**
     * What a saved transaction's SMS says, read again with the current rules; [bank] is its
     * account's. Null when no rule reads it now.
     */
    suspend fun reread(body: String, bank: String?, at: Long): SmsTransaction? =
        parser().readWithoutSender(body, at, bank)?.toTransaction(body)

    /** How many of the SMS waiting in To review [rule] can read, before it's used on them. */
    suspend fun unparsedReadableBy(rule: CompiledRule): Int {
        val parser = SmsParser(listOf(rule), filters = settings.filters.value)
        return unparsed.getAll().count {
            parser.parse(it.sender, it.body, it.receivedAt) is ParseResult.Parsed
        }
    }

    /**
     * Reads the SMS waiting in To review again with the current rules, after one was added or
     * edited. Each
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

/** [SmsIngestor.explain]'s answer. */
data class SmsExplanation(
    val result: ParseResult,
    /** The rules tried for the sender, in order; a rule that read the SMS is the last. */
    val rulesTried: List<TriedRule>,
    /** What saving it would do, when a rule read it. */
    val preview: SmsImportPreview?
)

/** A rule by id, and whether it's one the user added (Settings > Parsers) or built in. */
data class TriedRule(val id: String, val custom: Boolean)

enum class IngestOutcome {
    RECORDED,

    /** Saved, from a payee the user hasn't named yet. */
    RECORDED_FOR_REVIEW,
    ALREADY_THERE,

    /** Got past the filters, but no rule could read it. */
    UNREADABLE,

    /** Dropped by a filter: a sender switched off, or not a transaction (an OTP, an offer…). */
    FILTERED
}
