package com.openhand.khata.sms.parser

import com.google.re2j.Matcher
import com.google.re2j.Pattern
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Direction
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * The rule engine: sender + SMS text → a transaction, or a reason there isn't one (PRD feature 7).
 * Pure Kotlin, so it runs in unit tests on a computer exactly as on the phone.
 *
 * Rules are tried in the order given (custom rules first, then built-in ones), and the first rule
 * whose sender and pattern match wins.
 *
 * Patterns run on RE2J, not java.util.regex: RE2 matches in time proportional to the SMS length
 * for any pattern, so a badly written custom rule can't freeze the app. (Java's backtracking
 * engine can take exponential time, and Android's ICU-based one can't be interrupted.)
 */
class SmsParser(
    private val rules: List<CompiledRule>,
    private val zone: ZoneId = ZoneId.systemDefault(),
    val filters: SmsFilters = SmsFilters()
) {
    /**
     * Whether the sender filters let [sender] through. SMS import checks this first, so it can
     * skip everyone else's messages without reading their text.
     */
    fun accepts(sender: String): Boolean = filters.senderReason(sender) == null

    /** The bank the first rule for [sender] names, or null for a sender the rules don't know. */
    fun bankOf(sender: String): String? = SenderId.parse(sender)?.let { id ->
        rules.firstOrNull { id.header in it.headers }?.rule?.bank
    }

    /**
     * Sender filters, then the rules for the sender, then content filters on what no rule read.
     *
     * @param receivedAt when the phone received the SMS, epoch millis. Used as the transaction
     *   time unless the SMS has a date on a different day.
     */
    fun parse(sender: String, body: String, receivedAt: Long): ParseResult {
        filters.senderReason(sender)?.let { return ParseResult.Filtered(it) }
        val header = requireNotNull(SenderId.parse(sender)).header
        val candidates = rules.filter { header in it.headers }
        val parsed = candidates.firstNotNullOfOrNull { tryRule(it, body, receivedAt) }
        val dropped = if (parsed == null) filters.contentReason(body) else null
        return when {
            parsed != null -> ParseResult.Parsed(parsed)
            dropped != null -> ParseResult.Filtered(dropped)
            else -> ParseResult.Unparsed(candidates.firstOrNull()?.rule?.bank)
        }
    }

    private fun tryRule(compiled: CompiledRule, body: String, receivedAt: Long): ParsedSms? {
        val match = compiled.pattern.matcher(body).takeIf(Matcher::find) ?: return null
        val groups = compiled.pattern.namedGroups()
        fun named(name: String): String? = if (name in groups) match.group(name) else null

        val rule = compiled.rule
        val amount = named(RuleFormat.AMOUNT)?.let(Fields::amountPaise)
        val direction = direction(rule, named(RuleFormat.DIR) ?: body)
        return if (amount == null || direction == null) {
            null
        } else {
            ParsedSms(
                ruleId = rule.id,
                bank = rule.bank,
                amountPaise = amount,
                direction = direction,
                accountType = rule.accountType.accountType,
                accountLast4 = named(RuleFormat.ACCOUNT)?.let(Fields::last4),
                payee = named(RuleFormat.PAYEE)?.let(Fields::clean),
                reference = named(RuleFormat.REF)?.let(Fields::clean),
                timestamp = timestamp(rule.dateFormat, named(RuleFormat.DATE), receivedAt)
            )
        }
    }

    /** [rule]'s fixed direction, or the first of its words found in [text]. */
    private fun direction(rule: ParserRule, text: String): Direction? {
        val lower = text.lowercase(Locale.ROOT)
        val words = rule.directionWords.orEmpty()
        val fromWords = DIRECTION_ORDER.firstOrNull { dir ->
            words[dir].orEmpty().any { lower.contains(it.lowercase(Locale.ROOT)) }
        }
        return (rule.direction ?: fromWords)?.direction
    }

    /**
     * The SMS date if the rule reads one and it parses, with its time of day if the format has
     * one. A date on the same day as [receivedAt] keeps the more exact received time; an earlier
     * day gets the received time of day. An unreadable date falls back to [receivedAt].
     */
    private fun timestamp(format: String?, text: String?, receivedAt: Long): Long {
        val received = Instant.ofEpochMilli(receivedAt).atZone(zone)
        val dateTime = if (format != null &&
            text != null
        ) {
            smsDateTime(format, text, received)
        } else {
            null
        }
        return dateTime?.atZone(zone)?.toInstant()?.toEpochMilli() ?: receivedAt
    }

    private fun smsDateTime(format: String, text: String, received: ZonedDateTime): LocalDateTime? =
        try {
            val formatter = DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern(format)
                .toFormatter(Locale.ENGLISH)
            val parsed = formatter.parse(text.trim())
            val date = LocalDate.from(parsed)
            when {
                parsed.isSupported(
                    ChronoField.HOUR_OF_DAY
                ) -> LocalDateTime.of(date, LocalTime.from(parsed))
                date == received.toLocalDate() -> null
                else -> LocalDateTime.of(date, received.toLocalTime())
            }
        } catch (_: DateTimeException) {
            null
        }

    companion object {
        /** Every pattern is case-insensitive; rules can't set flags (see [RegexSubset]). */
        const val PATTERN_FLAGS: Int = Pattern.CASE_INSENSITIVE

        /** Refund first: a refund SMS often also says "credited". */
        private val DIRECTION_ORDER =
            listOf(RuleDirection.REFUND, RuleDirection.DEBIT, RuleDirection.CREDIT)
    }
}

sealed interface ParseResult {
    data class Parsed(val sms: ParsedSms) : ParseResult

    /** Dropped by one of the [SmsFilters], for [reason]. Never stored. */
    data class Filtered(val reason: FilterReason) : ParseResult

    /**
     * No rule read it and the filters kept it: it may be a transaction. Goes to the review inbox.
     * [bank] is null for a sender no rule knows.
     */
    data class Unparsed(val bank: String?) : ParseResult
}

/** What a rule read from one SMS. The balance is never included. */
data class ParsedSms(
    val ruleId: String,
    val bank: String,
    val amountPaise: Long,
    val direction: Direction,
    val accountType: AccountType,
    val accountLast4: String?,
    val payee: String?,
    val reference: String?,
    /** Epoch millis. */
    val timestamp: Long
)
