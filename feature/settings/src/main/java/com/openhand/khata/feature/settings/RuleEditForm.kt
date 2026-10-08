package com.openhand.khata.feature.settings

import com.openhand.khata.sms.parser.ParserRule
import com.openhand.khata.sms.parser.RuleAccountType
import com.openhand.khata.sms.parser.RuleCheck
import com.openhand.khata.sms.parser.RuleDirection
import com.openhand.khata.sms.parser.RuleFormat
import com.openhand.khata.sms.parser.RuleValidator
import java.util.Locale

/**
 * Settings > Parsers > Edit, for built-in and custom rules alike. The rule id can't change: it's
 * what a custom rule is replaced by, and what a built-in rule's edit is stored under.
 */
data class RuleEditForm(
    val id: String,
    val bank: String,
    val senders: List<String>,
    /** A fixed direction, or [words] that decide it. */
    val fixedDirection: Boolean,
    val direction: RuleDirection,
    /** As typed: words separated by commas, for each direction. */
    val words: Map<RuleDirection, String>,
    val accountType: RuleAccountType,
    val dateFormat: String,
    val pattern: String
) {
    /** Whether the pattern has a date group, so the date format is needed. */
    val hasDate: Boolean get() = RuleValidator.groups(pattern)?.contains(RuleFormat.DATE) == true

    fun addSender(text: String): RuleEditForm {
        val sender = text.trim().uppercase(Locale.ROOT)
        return if (sender.isEmpty() || sender in senders) this else copy(senders = senders + sender)
    }

    fun removeSender(sender: String): RuleEditForm = copy(senders = senders - sender)

    /** The rule as edited. It still has to pass [RuleValidator]. */
    fun rule(): ParserRule = ParserRule(
        v = RuleFormat.VERSION,
        id = id,
        bank = bank.trim(),
        senders = senders,
        pattern = pattern,
        direction = direction.takeIf { fixedDirection },
        directionWords = if (fixedDirection) {
            null
        } else {
            words.mapValues { (_, text) ->
                text.split(',').map(String::trim).filter(String::isNotEmpty)
            }
                .filterValues { it.isNotEmpty() }
        },
        accountType = accountType,
        // A format left over after the date group was taken out of the pattern isn't needed.
        dateFormat = dateFormat.trim().takeIf { it.isNotEmpty() && hasDate }
    )

    fun check(): RuleCheck = RuleValidator.validate(rule())

    /** A pattern made again with the rule maker; the rest of the rule stays. */
    fun remarked(pattern: String, dateFormat: String?): RuleEditForm =
        copy(pattern = pattern, dateFormat = dateFormat.orEmpty())

    companion object {
        fun from(rule: ParserRule): RuleEditForm = RuleEditForm(
            id = rule.id,
            bank = rule.bank,
            senders = rule.senders,
            fixedDirection = rule.direction != null,
            direction = rule.direction ?: RuleDirection.DEBIT,
            words = rule.directionWords.orEmpty().mapValues { (_, words) ->
                words.joinToString(", ")
            },
            accountType = rule.accountType,
            dateFormat = rule.dateFormat.orEmpty(),
            pattern = rule.pattern
        )
    }
}
