package com.openhand.khata.sms.parser

import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException
import com.openhand.khata.core.model.SenderCategory
import com.openhand.khata.core.model.SenderId
import java.time.format.DateTimeFormatter

/**
 * Checks a rule before the engine will use it, for built-in and pasted rules alike. Only a
 * [CompiledRule] can be given to [SmsParser], so an unchecked rule can never run.
 *
 * There is no speed check: patterns run on RE2J, where matching time grows only with the length
 * of the SMS, so no pattern can freeze the app.
 */
object RuleValidator {
    const val MAX_PATTERN_LENGTH = 1_000
    private const val MAX_ID_LENGTH = 64
    private const val MAX_BANK_LENGTH = 40
    private const val MAX_SENDERS = 10
    private const val MAX_WORDS = 20
    private val ID = Regex("[a-z0-9][a-z0-9-]*")

    fun validate(rule: ParserRule): RuleCheck {
        val errors = mutableListOf<RuleError>()
        if (rule.v != RuleFormat.VERSION) errors += RuleError.UNKNOWN_VERSION
        if (rule.id.length > MAX_ID_LENGTH || !ID.matches(rule.id)) errors += RuleError.BAD_ID
        if (rule.bank.isBlank() || rule.bank.length > MAX_BANK_LENGTH) errors += RuleError.BAD_BANK
        val senders = rule.senders.mapNotNull {
            SenderId.parse(it)?.takeUnless { id -> id.category == SenderCategory.PROMOTIONAL }
        }
        if (senders.isEmpty() || senders.size != rule.senders.size || senders.size > MAX_SENDERS) {
            errors += RuleError.BAD_SENDERS
        }
        if (!directionValid(rule)) errors += RuleError.DIRECTION
        if (rule.dateFormat != null && !dateFormatValid(rule.dateFormat)) {
            errors += RuleError.BAD_DATE_FORMAT
        }
        val pattern = compile(rule, errors)
        return if (errors.isEmpty() && pattern != null) {
            RuleCheck.Valid(
                CompiledRule(
                    rule,
                    pattern,
                    senders.mapTo(mutableSetOf()) {
                        it.header
                    }
                )
            )
        } else {
            RuleCheck.Invalid(errors.distinct())
        }
    }

    /** The named groups in [pattern], or null if it uses something outside the allowed subset. */
    fun groups(pattern: String): Set<String>? = RegexSubset.namedGroups(pattern)

    /** Exactly one of `direction` and `directionWords`, with no empty or blank word lists. */
    private fun directionValid(rule: ParserRule): Boolean {
        val words = rule.directionWords
        val wordsValid = words == null ||
            words.isNotEmpty() &&
            words.values.all { list ->
                list.size in 1..MAX_WORDS && list.none(String::isBlank)
            }
        return (rule.direction == null) != (words == null) && wordsValid
    }

    private fun dateFormatValid(format: String): Boolean = try {
        DateTimeFormatter.ofPattern(format)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    /** The compiled pattern, or null with the reason added to [errors]. */
    private fun compile(rule: ParserRule, errors: MutableList<RuleError>): Pattern? {
        val groups = RegexSubset.namedGroups(rule.pattern)
        val pattern = when {
            rule.pattern.isEmpty() || rule.pattern.length > MAX_PATTERN_LENGTH -> {
                errors += RuleError.PATTERN_TOO_LONG
                null
            }
            groups == null -> {
                errors += RuleError.UNSUPPORTED_PATTERN
                null
            }
            else -> compileOrNull(rule.pattern) ?: null.also { errors += RuleError.BAD_PATTERN }
        }
        if (pattern != null && groups != null) errors += groupErrors(rule, groups)
        return pattern
    }

    private fun compileOrNull(pattern: String): Pattern? = try {
        Pattern.compile(pattern, SmsParser.PATTERN_FLAGS)
    } catch (_: PatternSyntaxException) {
        null
    }

    private fun groupErrors(rule: ParserRule, groups: Set<String>): List<RuleError> = buildList {
        if (RuleFormat.AMOUNT !in groups) add(RuleError.MISSING_AMOUNT)
        val hasDate = RuleFormat.DATE in groups
        if (rule.dateFormat != null && !hasDate) add(RuleError.DATE_FORMAT_WITHOUT_DATE)
        if (hasDate && rule.dateFormat == null) add(RuleError.DATE_WITHOUT_FORMAT)
    }
}

/** A rule that passed [RuleValidator], with its pattern compiled once. */
class CompiledRule internal constructor(
    val rule: ParserRule,
    internal val pattern: Pattern,
    /** [ParserRule.senders] as parsed headers. */
    val headers: Set<String>
)

sealed interface RuleCheck {
    data class Valid(val rule: CompiledRule) : RuleCheck

    data class Invalid(val errors: List<RuleError>) : RuleCheck
}

/** Stable codes: the app maps them to translated messages (docs/parser-rules.md lists them). */
enum class RuleError(val code: String) {
    UNKNOWN_VERSION("unknown_version"),
    BAD_ID("bad_id"),
    BAD_BANK("bad_bank"),
    BAD_SENDERS("bad_senders"),

    /** Neither or both of `direction` and `directionWords`, or an empty word list. */
    DIRECTION("direction"),
    BAD_DATE_FORMAT("bad_date_format"),
    PATTERN_TOO_LONG("pattern_too_long"),

    /** Uses a regex feature outside the allowed subset, or an unknown group name. */
    UNSUPPORTED_PATTERN("unsupported_pattern"),
    BAD_PATTERN("bad_pattern"),
    MISSING_AMOUNT("missing_amount"),
    DATE_FORMAT_WITHOUT_DATE("date_format_without_date"),
    DATE_WITHOUT_FORMAT("date_without_format")
}
