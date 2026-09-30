package com.openhand.khata.feature.settings

import com.openhand.khata.sms.parser.DateFormats
import com.openhand.khata.sms.parser.RuleAccountType
import com.openhand.khata.sms.parser.RuleCheck
import com.openhand.khata.sms.parser.RuleDirection
import com.openhand.khata.sms.parser.RuleMaker
import com.openhand.khata.sms.parser.RuleValidator
import com.openhand.khata.sms.parser.SenderId
import java.util.Locale

/**
 * What the user has told the rule maker so far: the SMS, the words marked in it, and the rule's
 * details. [selection] is the run of words picked but not yet marked, as word indexes.
 */
data class MakerForm(
    /** Null for a pasted SMS. */
    val sender: String?,
    val body: String,
    val receivedAt: Long,
    val marks: List<RuleMaker.Mark> = emptyList(),
    val selection: IntRange? = null,
    val direction: RuleDirection = RuleDirection.DEBIT,
    val accountType: RuleAccountType = RuleAccountType.BANK,
    val bank: String = "",
    /** As typed: sender IDs separated by commas or spaces. */
    val senders: String = "",
    val dateFormat: String? = null
) {
    val words: List<RuleMaker.Word> = RuleMaker.words(body)

    /**
     * A tap on word [index]: selects it; with one word selected, a tap on another selects the run
     * between them; a tap on the only selected word clears the selection.
     */
    fun tap(index: Int): MakerForm {
        val current = selection
        val next = when {
            current == null || current.first != current.last -> index..index
            current.first == index -> null
            else -> minOf(current.first, index)..maxOf(current.first, index)
        }
        return copy(selection = next)
    }

    /**
     * Marks the selected words as [field]. An earlier mark of the same field, or one overlapping
     * these words, is replaced. Marking a date picks the first common format that reads it.
     */
    fun mark(field: RuleMaker.Field): MakerForm {
        val picked = selection ?: return this
        val mark = RuleMaker.Mark(field, words[picked.first].start, words[picked.last].end)
        val kept = marks.filterNot {
            it.field == field || it.start < mark.end && mark.start < it.end
        }
        val format = if (field == RuleMaker.Field.DATE) {
            DateFormats.matching(body.substring(mark.start, mark.end)).firstOrNull()
        } else {
            dateFormat.takeIf { kept.any { it.field == RuleMaker.Field.DATE } }
        }
        return copy(marks = kept + mark, selection = null, dateFormat = format)
    }

    fun unmark(field: RuleMaker.Field): MakerForm = copy(
        marks = marks.filterNot { it.field == field },
        dateFormat = dateFormat.takeIf { field != RuleMaker.Field.DATE }
    )

    /** The field word [index] is marked as, if any. */
    fun fieldAt(index: Int): RuleMaker.Field? {
        val word = words[index]
        return marks.firstOrNull { it.start < word.end && word.start < it.end }?.field
    }

    fun markedText(mark: RuleMaker.Mark): String = body.substring(mark.start, mark.end)

    fun senderList(): List<String> = senders.split(',', ' ', '\n')
        .map { it.trim().uppercase(Locale.ROOT) }
        .filter(String::isNotEmpty)

    /** The rule, with an id not in [taken]. Null until the amount is marked. */
    fun check(taken: Set<String>): RuleCheck? {
        if (marks.none { it.field == RuleMaker.Field.AMOUNT }) return null
        val draft = RuleMaker.Draft(
            id = RuleMaker.ruleId(bank, direction, taken),
            bank = bank,
            senders = senderList(),
            body = body,
            marks = marks,
            direction = direction,
            accountType = accountType,
            dateFormat = dateFormat?.trim()?.takeIf(String::isNotEmpty)
        )
        return RuleValidator.validate(RuleMaker.rule(draft))
    }

    companion object {
        /**
         * A new form for this SMS, with the direction and account type guessed from its words.
         * [knownBank] is the bank the rules already name for [sender]; otherwise its sender ID.
         */
        fun start(sender: String?, body: String, receivedAt: Long, knownBank: String?): MakerForm {
            val header = sender?.let(SenderId::parse)?.header.orEmpty()
            return MakerForm(
                sender = sender,
                body = body,
                receivedAt = receivedAt,
                direction = RuleMaker.guessDirection(body),
                accountType = RuleMaker.guessAccountType(body),
                bank = knownBank ?: header,
                senders = header
            )
        }
    }
}
