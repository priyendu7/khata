package com.openhand.khata.feature.settings

import com.openhand.khata.sms.parser.ParserRule
import com.openhand.khata.sms.parser.RuleAccountType
import com.openhand.khata.sms.parser.RuleCheck
import com.openhand.khata.sms.parser.RuleDirection
import com.openhand.khata.sms.parser.RuleFormat
import com.openhand.khata.sms.parser.RuleKind
import com.openhand.khata.sms.parser.RuleValidator
import java.util.Locale

/**
 * Settings > Parsers > Edit, for built-in and custom rules alike. The rule id can't change: it's
 * what a custom rule is replaced by, and what a built-in rule's edit is stored under. Nor can its
 * [kind]: a statement rule has no direction or account type, but a due date format instead.
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
    val pattern: String,
    val kind: RuleKind = RuleKind.TRANSACTION,
    val dueDateFormat: String = ""
) {
    val isStatement: Boolean get() = kind == RuleKind.STATEMENT

    private val groups: Set<String> get() = RuleValidator.groups(pattern).orEmpty()

    /**
     * Whether the pattern has a date group (`statementDate` in a statement), so the date format
     * is needed.
     */
    val hasDate: Boolean
        get() = (if (isStatement) RuleFormat.STATEMENT_DATE else RuleFormat.DATE) in groups

    /** Whether a statement's pattern has its due date, so the due date format is needed. */
    val hasDueDate: Boolean get() = isStatement && RuleFormat.DUE_DATE in groups

    fun addSender(text: String): RuleEditForm {
        val sender = text.trim().uppercase(Locale.ROOT)
        return if (sender.isEmpty() || sender in senders) this else copy(senders = senders + sender)
    }

    fun removeSender(sender: String): RuleEditForm = copy(senders = senders - sender)

    /**
     * The rule as edited, in the oldest format version that holds it, so a v1 rule stays
     * readable by older versions of Khata. It still has to pass [RuleValidator].
     */
    fun rule(): ParserRule {
        val rule = ParserRule(
            v = RuleFormat.VERSION,
            id = id,
            bank = bank.trim(),
            senders = senders,
            pattern = pattern,
            kind = RuleKind.STATEMENT.takeIf { isStatement },
            direction = direction.takeIf { fixedDirection && !isStatement },
            directionWords = if (fixedDirection || isStatement) {
                null
            } else {
                words.mapValues { (_, text) ->
                    text.split(',').map(String::trim).filter(String::isNotEmpty)
                }
                    .filterValues { it.isNotEmpty() }
            },
            accountType = accountType.takeUnless { isStatement },
            // A format left over after its group was taken out of the pattern isn't needed.
            dateFormat = dateFormat.trim().takeIf { it.isNotEmpty() && hasDate },
            dueDateFormat = dueDateFormat.trim().takeIf { it.isNotEmpty() && hasDueDate }
        )
        return rule.copy(v = RuleFormat.versionFor(rule))
    }

    fun check(): RuleCheck = RuleValidator.validate(rule())

    /** A pattern made again with the rule maker ([remade]); the rest of the rule stays. */
    fun remarked(remade: ParserRule): RuleEditForm = copy(
        pattern = remade.pattern,
        dateFormat = remade.dateFormat.orEmpty(),
        dueDateFormat = remade.dueDateFormat.orEmpty()
    )

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
            accountType = rule.accountType ?: RuleAccountType.CREDIT_CARD,
            dateFormat = rule.dateFormat.orEmpty(),
            pattern = rule.pattern,
            kind = rule.kind ?: RuleKind.TRANSACTION,
            dueDateFormat = rule.dueDateFormat.orEmpty()
        )
    }
}
