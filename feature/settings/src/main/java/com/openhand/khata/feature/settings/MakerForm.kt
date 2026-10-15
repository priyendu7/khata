package com.openhand.khata.feature.settings

import com.openhand.khata.core.model.SenderId
import com.openhand.khata.sms.parser.DateFormats
import com.openhand.khata.sms.parser.FieldGuesser
import com.openhand.khata.sms.parser.ParserRule
import com.openhand.khata.sms.parser.RuleAccountType
import com.openhand.khata.sms.parser.RuleCheck
import com.openhand.khata.sms.parser.RuleDirection
import com.openhand.khata.sms.parser.RuleKind
import com.openhand.khata.sms.parser.RuleMaker
import com.openhand.khata.sms.parser.RuleValidator
import java.util.Locale

/**
 * What the user has told the rule maker so far: the SMS, the words marked in it, and the rule's
 * details. [selection] is the run of words picked but not yet marked, as word indexes.
 *
 * Fields are marked one at a time, in the order [RuleMaker.Field.of] gives for the rule's [kind]:
 * [step] is the one being marked, and null once each has been marked or skipped. The form starts
 * with [FieldGuesser]'s guesses marked; [guessed] are the ones the user hasn't confirmed or
 * changed yet.
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
    val revisiting: Boolean = false,
    val guessed: Set<RuleMaker.Field> = emptySet(),
    /** A transaction, or a card statement (#153). */
    val kind: RuleKind = RuleKind.TRANSACTION
) {
    val words: List<RuleMaker.Word> = RuleMaker.words(body)

    /** The fields this kind of rule marks, in order. */
    val fields: List<RuleMaker.Field> get() = RuleMaker.Field.of(kind)

    private val required: Set<RuleMaker.Field> get() = RuleMaker.Field.requiredFor(kind)

    val isStatement: Boolean get() = kind == RuleKind.STATEMENT

    /**
     * Marking a [kind] of rule instead: the SMS's fields are guessed again for it, and marking
     * starts over.
     */
    fun withKind(kind: RuleKind): MakerForm {
        if (kind == this.kind) return this
        val guesses = FieldGuesser.guess(body, kind)
        return copy(
            kind = kind,
            marks = guesses.marks,
            dateFormat = guesses.dateFormat,
            guessed = guesses.fields.keys,
            step = RuleMaker.Field.of(kind).first(),
            selection = null,
            revisiting = false,
            direction = guesses.direction ?: direction,
            accountType = guesses.accountType.takeIf { kind == RuleKind.TRANSACTION } ?: accountType
        )
    }

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
     * these words, is replaced. Marking a date (or due date) picks the first common format that
     * reads it.
     */
    fun mark(field: RuleMaker.Field): MakerForm {
        val picked = selection ?: return this
        val mark = RuleMaker.Mark(field, words[picked.first].start, words[picked.last].end)
        val kept = marks.filterNot {
            it.field == field || it.start < mark.end && mark.start < it.end
        }
        val format = if (field.isDate) {
            DateFormats.matching(body.substring(mark.start, mark.end)).firstOrNull()
        } else {
            dateFormat.takeIf { kept.any { it.field.isDate } }
        }
        return copy(marks = kept + mark, selection = null, dateFormat = format)
    }

    fun unmark(field: RuleMaker.Field): MakerForm = copy(
        marks = marks.filterNot { it.field == field },
        dateFormat = dateFormat.takeIf { !field.isDate }
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
        // A tap changes the field, so it's no longer a guess.
        return when {
            field == null || other -> this
            run == null -> tapped.unmark(field).copy(guessed = guessed - field)
            else -> tapped.mark(field).copy(selection = run, guessed = guessed - field)
        }
    }

    /**
     * The amount can't be skipped: a rule without one reads nothing. Nor can a statement's card,
     * total or due date.
     */
    val canSkip: Boolean get() = step !in required

    val canGoOn: Boolean get() = step.let { it == null || it !in required || isMarked(it) }

    fun isMarked(field: RuleMaker.Field): Boolean = marks.any { it.field == field }

    /** [field] is marked with a guess the user hasn't confirmed yet. */
    fun isGuess(field: RuleMaker.Field): Boolean = field in guessed && isMarked(field)

    /**
     * "Looks right": every guess at once, straight on to checking the rule. Needs an amount (a
     * statement's card, total and due date).
     */
    val canConfirmAll: Boolean
        get() = step != null && guessed.isNotEmpty() && required.all(::isMarked)

    fun confirmAll(): MakerForm = if (canConfirmAll) {
        copy(step = null, selection = null, revisiting = false, guessed = emptySet())
    } else {
        this
    }

    /** On to the next field (confirming its guess), or back to the rest after a chip's step. */
    fun next(): MakerForm = copy(
        step = if (revisiting) null else step?.let { fields.getOrNull(fields.indexOf(it) + 1) },
        selection = null,
        revisiting = false,
        guessed = guessed - setOfNotNull(step)
    )

    /** Leaves the step's field unmarked and goes on. */
    fun skip(): MakerForm = step?.takeIf { canSkip }?.let { unmark(it).next() } ?: this

    /** The step before, keeping what's marked. From the rest, the last step. */
    fun back(): MakerForm = copy(
        step = step?.let { fields.getOrNull(fields.indexOf(it) - 1) ?: it } ?: fields.last(),
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

    /**
     * The rule, with an id not in [taken]. Null until the amount (a statement's card, total and
     * due date) is marked.
     */
    fun check(taken: Set<String>): RuleCheck? {
        if (!required.all(::isMarked)) return null
        val id = fixedId ?: RuleMaker.ruleId(bank, direction.takeUnless { isStatement }, taken)
        val draft = RuleMaker.Draft(
            id = id,
            bank = bank,
            senders = senderList(),
            body = body,
            marks = marks,
            direction = direction,
            accountType = accountType,
            dateFormat = dateFormat?.trim()?.takeIf(String::isNotEmpty),
            kind = kind
        )
        return RuleValidator.validate(RuleMaker.rule(draft))
    }

    /** Re-marking [rule]: its id, kind, bank, senders and the rest stay as they are. */
    fun remarking(rule: ParserRule): MakerForm = withKind(rule.kind ?: RuleKind.TRANSACTION).copy(
        fixedId = rule.id,
        bank = rule.bank,
        senders = rule.senders.joinToString(", "),
        direction = rule.direction ?: direction,
        accountType = rule.accountType ?: accountType
    )

    companion object {
        /**
         * A new form for this SMS, with its kind, fields, direction and account type guessed from
         * its words ([FieldGuesser]). [knownBank] is the bank the rules already name for [sender];
         * otherwise its sender ID.
         */
        fun start(sender: String?, body: String, receivedAt: Long, knownBank: String?): MakerForm {
            val header = sender?.let(SenderId::parse)?.header.orEmpty()
            val guesses = FieldGuesser.guess(body)
            return MakerForm(
                sender = sender,
                body = body,
                receivedAt = receivedAt,
                marks = guesses.marks,
                direction = guesses.direction ?: RuleMaker.guessDirection(body),
                accountType = guesses.accountType,
                bank = knownBank ?: header,
                senders = header,
                dateFormat = guesses.dateFormat,
                guessed = guesses.fields.keys,
                kind = guesses.kind,
                step = RuleMaker.Field.of(guesses.kind).first()
            )
        }
    }
}
