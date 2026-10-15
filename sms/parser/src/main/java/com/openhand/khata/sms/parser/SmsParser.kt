package com.openhand.khata.sms.parser

import com.google.re2j.Matcher
import com.google.re2j.Pattern
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.SenderCategory
import com.openhand.khata.core.model.SenderId
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
 * whose sender and pattern match wins. Rules for any sender ([ParserRule.anySender], such as the
 * generic card rules) come after every rule that names the sender, so a bank's own rules win.
 * Statement rules are tried like the rest, so a statement is read before the content filters
 * could drop it as a bill reminder.
 *
 * Patterns run on RE2J, not java.util.regex: RE2 matches in time proportional to the SMS length
 * for any pattern, so a badly written custom rule can't freeze the app. (Java's backtracking
 * engine can take exponential time, and Android's ICU-based one can't be interrupted.)
 */
class SmsParser(
    private val rules: List<CompiledRule>,
    private val zone: ZoneId = ZoneId.systemDefault(),
    val filters: SmsFilters = SmsFilters(),
    private val ignore: IgnoreRules = IgnoreRules()
) {
    /**
     * Whether the sender filters let [sender] through. SMS import checks this first, so it can
     * skip everyone else's messages without reading their text.
     */
    fun accepts(sender: String): Boolean = senderReason(sender) == null

    /** The sender filters, then the senders the user ignored. */
    private fun senderReason(sender: String): FilterReason? = filters.senderReason(sender)
        ?: ignore.senderRule(requireNotNull(SenderId.parse(sender)).header)
            ?.let(FilterReason::IgnoredSender)

    /** The bank the first rule for [sender] names, or null for a sender the rules don't know. */
    fun bankOf(sender: String): String? = SenderId.parse(sender)?.let { id ->
        rules.firstOrNull { id.header in it.headers }?.rule?.bank
    }

    /** The rules that name [id]'s header, then those for any business sender. */
    private fun rulesFor(id: SenderId): List<CompiledRule> {
        val (any, named) = rules.partition { it.rule.anySender }
        val business = id.category != SenderCategory.PROMOTIONAL &&
            id.category != SenderCategory.GOVERNMENT
        return named.filter { id.header in it.headers } + any.takeIf { business }.orEmpty()
    }

    /**
     * Sender filters, then the rules for the sender, then content filters on what no rule read.
     *
     * @param receivedAt when the phone received the SMS, epoch millis. Used as the transaction
     *   time unless the SMS has a date on a different day.
     */
    fun parse(sender: String, body: String, receivedAt: Long): ParseResult =
        explain(sender, body, receivedAt).result

    /**
     * [parse], with the rules it tried, for Settings > SMS import > Test a message. [parse] goes
     * through here, so the two can't disagree.
     */
    fun explain(sender: String, body: String, receivedAt: Long): Explanation {
        senderReason(sender)?.let { return Explanation(ParseResult.Filtered(it), emptyList()) }
        val id = requireNotNull(SenderId.parse(sender))
        val candidates = rulesFor(id)
        val tried = mutableListOf<String>()
        val read = candidates.firstNotNullOfOrNull { rule ->
            tried += rule.rule.id
            tryRule(rule, body, receivedAt)
        }
        val dropped = if (read == null) contentReason(id.header, body) else null
        val result = when {
            read != null -> read
            dropped != null -> ParseResult.Filtered(dropped)
            // A rule for any sender says nothing about whose sender this is.
            else -> ParseResult.Unparsed(candidates.firstOrNull { !it.rule.anySender }?.rule?.bank)
        }
        return Explanation(result, tried)
    }

    /**
     * Reads [body] without its sender, which a saved transaction doesn't keep (#113): the rules
     * for [bank] first (either name may be the longer one, as in "Kotak Mahindra Bank"), then
     * every other rule. No filters: the SMS was saved already. Only transaction rules.
     */
    fun readWithoutSender(body: String, receivedAt: Long, bank: String?): ParsedSms? {
        val (ofBank, others) = rules.filterNot { it.rule.isStatement }.partition { compiled ->
            val rule = compiled.rule.bank
            bank != null &&
                (rule.contains(bank, ignoreCase = true) || bank.contains(rule, ignoreCase = true))
        }
        return (ofBank + others).firstNotNullOfOrNull {
            (tryRule(it, body, receivedAt) as? ParseResult.Parsed)?.sms
        }
    }

    /** The content filters, then the user's "ignore messages like this" rules. */
    private fun contentReason(header: String, body: String): FilterReason? =
        filters.contentReason(body)
            ?: ignore.likeThisRule(header, body)?.let(FilterReason::IgnoredLikeThis)

    /** What [compiled] reads from [body]: a transaction or a statement, or null. */
    private fun tryRule(compiled: CompiledRule, body: String, receivedAt: Long): ParseResult? {
        val match = compiled.pattern.matcher(body).takeIf(Matcher::find) ?: return null
        val groups = compiled.pattern.namedGroups()
        val read = Groups { name -> if (name in groups) match.group(name) else null }
        return if (compiled.rule.isStatement) {
            statement(compiled.rule, read)?.let(ParseResult::Statement)
        } else {
            transaction(compiled.rule, read, body, receivedAt)?.let(ParseResult::Parsed)
        }
    }

    /** A matched rule's named groups by name; null for a group the pattern doesn't have. */
    private fun interface Groups {
        operator fun get(name: String): String?
    }

    private fun bank(rule: ParserRule, read: Groups): String =
        read[RuleFormat.BANK]?.let(Fields::clean) ?: rule.bank

    private fun transaction(
        rule: ParserRule,
        read: Groups,
        body: String,
        receivedAt: Long
    ): ParsedSms? {
        val amount = read[RuleFormat.AMOUNT]?.let(Fields::amountPaise)
        val direction = direction(rule, read[RuleFormat.DIR] ?: body)
        val accountType = rule.accountType
        return if (amount == null || direction == null || accountType == null) {
            null
        } else {
            ParsedSms(
                ruleId = rule.id,
                bank = bank(rule, read),
                amountPaise = amount,
                direction = direction,
                accountType = accountType.accountType,
                accountLast4 = read[RuleFormat.ACCOUNT]?.let(Fields::last4),
                linkedAccountLast4 = read[RuleFormat.LINKED_ACCOUNT]?.let(Fields::last4),
                payee = read[RuleFormat.PAYEE]?.let(Fields::clean),
                reference = read[RuleFormat.REF]?.let(Fields::clean),
                timestamp = timestamp(rule.dateFormat, read[RuleFormat.DATE], receivedAt)
            )
        }
    }

    /** A statement needs its card, total and a readable due date; the rest is optional. */
    private fun statement(rule: ParserRule, read: Groups): ParsedStatement? {
        val card = read[RuleFormat.ACCOUNT]?.let(Fields::last4)
        val total = read[RuleFormat.TOTAL]?.let(Fields::amountPaise)
        val due = localDate(rule.dueDateFormat, read[RuleFormat.DUE_DATE])
        return if (card == null || total == null || due == null) {
            null
        } else {
            ParsedStatement(
                ruleId = rule.id,
                bank = bank(rule, read),
                cardLast4 = card,
                totalPaise = total,
                minDuePaise = read[RuleFormat.MIN_DUE]?.let(Fields::amountPaise),
                dueDate = due,
                statementDate = localDate(rule.dateFormat, read[RuleFormat.STATEMENT_DATE])
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
            val parsed = formatter(format).parse(text.trim())
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

    private fun localDate(format: String?, text: String?): LocalDate? = try {
        text?.let { LocalDate.from(formatter(format ?: return null).parse(it.trim())) }
    } catch (_: DateTimeException) {
        null
    }

    private fun formatter(format: String) = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern(format)
        .toFormatter(Locale.ENGLISH)

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

    /** A credit card statement a statement rule read. Not saved until #154. */
    data class Statement(val statement: ParsedStatement) : ParseResult

    /** Dropped by one of the [SmsFilters], for [reason]. Never stored. */
    data class Filtered(val reason: FilterReason) : ParseResult

    /**
     * No rule read it and the filters kept it: it may be a transaction. Goes to the review inbox.
     * [bank] is null for a sender no rule knows.
     */
    data class Unparsed(val bank: String?) : ParseResult
}

/**
 * What [SmsParser.parse] did with one SMS, and the ids of the rules it tried, in order. When a
 * rule read it, that rule is the last one tried.
 */
data class Explanation(val result: ParseResult, val rulesTried: List<String>)

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
    val timestamp: Long,
    /** The bank account a debit card spend is from (#155), when the SMS names it. */
    val linkedAccountLast4: String? = null
)

/** What a statement rule read from one SMS. */
data class ParsedStatement(
    val ruleId: String,
    val bank: String,
    val cardLast4: String,
    val totalPaise: Long,
    /** Null when the rule has no `minDue` group. */
    val minDuePaise: Long?,
    val dueDate: LocalDate,
    val statementDate: LocalDate?
)
