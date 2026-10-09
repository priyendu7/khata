package com.openhand.khata.feature.settings

import com.openhand.khata.core.model.SenderId
import com.openhand.khata.sms.parser.DateFormats
import com.openhand.khata.sms.parser.ParserRule
import com.openhand.khata.sms.parser.RuleAccountType
import com.openhand.khata.sms.parser.RuleCheck
import com.openhand.khata.sms.parser.RuleDirection
import com.openhand.khata.sms.parser.RuleMaker
import com.openhand.khata.sms.parser.RuleValidator
import java.util.Locale

/**
 * What the user has told the rule maker so far: the SMS, the words marked in it, and the rule's
 * details. [selection] is the run of words picked but not yet marked, as word indexes.
 *
 * Fields are marked one at a time, in [RuleMaker.Field] order: [step] is the one being marked,
 * and null once each has been marked or skipped.
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
    val dateFormat: String? = null,
    /** Re-marking a rule (Settings > Parsers > Edit): its id stays. */
    val fixedId: String? = null,
    val step: RuleMaker.Field? = RuleMaker.Field.AMOUNT,
    /** The step was opened again from its chip, so finishing it goes back to the rest. */
    val revisiting: Boolean = false
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

    /**
     * A tap on word [index] while marking [step]: marks the word, or the run from the word tapped
     * before, as that field. A tap on the only marked word clears it. Words marked as another
     * field are left alone, and a run that would take them in starts again at [index] instead.
     */
    fun tapInStep(index: Int): MakerForm {
        val field = step
        val other = fieldAt(index).let { it != null && it != field }
        val tapped = tap(index).let { tapped ->
            val takesOthers = tapped.selection?.any { word ->
                fieldAt(word).let { it != null && it != field }
            } == true
            if (takesOthers) copy(selection = index..index) else tapped
        }
        val run = tapped.selection
        return when {
            field == null || other -> this
            run == null -> tapped.unmark(field)
            else -> tapped.mark(field).copy(selection = run)
        }
    }

    /** The amount can't be skipped: a rule without one reads nothing. */
    val canSkip: Boolean get() = step != RuleMaker.Field.AMOUNT

    val canGoOn: Boolean get() = step != RuleMaker.Field.AMOUNT || isMarked(RuleMaker.Field.AMOUNT)

    fun isMarked(field: RuleMaker.Field): Boolean = marks.any { it.field == field }

    /** On to the next field, or back to the rest after a step opened from its chip. */
    fun next(): MakerForm = copy(
        step = if (revisiting) null else step?.let { fieldNumber(it.ordinal + 1) },
        selection = null,
        revisiting = false
    )

    /** Leaves the step's field unmarked and goes on. */
    fun skip(): MakerForm = step?.takeIf { canSkip }?.let { unmark(it).next() } ?: this

    /** The step before, keeping what's marked. From the rest, the last step. */
    fun back(): MakerForm = copy(
        step = step?.let { fieldNumber(it.ordinal - 1) ?: it } ?: RuleMaker.Field.entries.last(),
        selection = null,
        revisiting = false
    )

    /** Opens [field]'s step again from its chip. */
    fun revisit(field: RuleMaker.Field): MakerForm =
        copy(step = field, selection = null, revisiting = true)

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
            id = fixedId ?: RuleMaker.ruleId(bank, direction, taken),
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

    /** Re-marking [rule]: its id, bank, senders and the rest stay as they are. */
    fun remarking(rule: ParserRule): MakerForm = copy(
        fixedId = rule.id,
        bank = rule.bank,
        senders = rule.senders.joinToString(", "),
        direction = rule.direction ?: direction,
        accountType = rule.accountType
    )

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

private fun fieldNumber(ordinal: Int): RuleMaker.Field? = RuleMaker.Field.entries.getOrNull(ordinal)
