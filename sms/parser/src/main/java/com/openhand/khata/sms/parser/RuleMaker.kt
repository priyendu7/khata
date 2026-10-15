package com.openhand.khata.sms.parser

import java.util.Locale

/**
 * Makes a parser rule from one SMS (PRD feature 8): the user marks which words are the amount,
 * payee and so on (or, for a card statement, the card, total, minimum and due date), and the rest
 * of the SMS around them becomes literal text. Pure Kotlin, so the
 * rule is checked by the same [RuleValidator] and [SmsParser] that run it.
 *
 * The pattern keeps a couple of words before the first marked part and one after the last, so it
 * matches this kind of SMS and not others from the same bank. Literal text is escaped, spaces match
 * any run of whitespace, and numbers in it (a balance, a time) match any number.
 */
object RuleMaker {
    /** What a marked part of the SMS is. [Field.of] says which a rule kind has. */
    enum class Field(val group: String) {
        AMOUNT(RuleFormat.AMOUNT),
        PAYEE(RuleFormat.PAYEE),
        ACCOUNT(RuleFormat.ACCOUNT),
        REF(RuleFormat.REF),
        DATE(RuleFormat.DATE),
        TOTAL(RuleFormat.TOTAL),
        MIN_DUE(RuleFormat.MIN_DUE),
        DUE_DATE(RuleFormat.DUE_DATE);

        /** Read with the rule's date format. */
        val isDate: Boolean get() = this == DATE || this == DUE_DATE

        companion object {
            private val TRANSACTION = listOf(AMOUNT, PAYEE, ACCOUNT, REF, DATE)
            private val STATEMENT = listOf(ACCOUNT, TOTAL, MIN_DUE, DUE_DATE)

            /** The fields a rule of [kind] marks, in the order they're asked for. */
            fun of(kind: RuleKind): List<Field> =
                if (kind == RuleKind.STATEMENT) STATEMENT else TRANSACTION

            /** The fields a rule of [kind] can't do without. */
            fun requiredFor(kind: RuleKind): Set<Field> =
                if (kind == RuleKind.STATEMENT) setOf(ACCOUNT, TOTAL, DUE_DATE) else setOf(AMOUNT)
        }
    }

    /** A word of the SMS: a run of non-space characters at [start] until [end] (exclusive). */
    data class Word(val text: String, val start: Int, val end: Int)

    /** Characters [start] until [end] (exclusive) of the SMS are [field]. */
    data class Mark(val field: Field, val start: Int, val end: Int)

    /**
     * Everything needed to make a rule. [dateFormat] is needed when a date (the due date, in a
     * statement) is marked. A statement has no direction or account type, so those are left out.
     */
    data class Draft(
        val id: String,
        val bank: String,
        val senders: List<String>,
        val body: String,
        val marks: List<Mark>,
        val direction: RuleDirection,
        val accountType: RuleAccountType,
        val dateFormat: String? = null,
        val kind: RuleKind = RuleKind.TRANSACTION
    )

    private const val WORDS_BEFORE = 2
    private const val MAX_ID_BASE = 50
    private val WORD = Regex("""\S+""")

    fun words(body: String): List<Word> =
        WORD.findAll(body).map { Word(it.value, it.range.first, it.range.last + 1) }.toList()

    /**
     * [draft] as a rule, using only the marks its kind has. A transaction rule is written as v1,
     * so older versions of Khata can read its code. It still has to pass [RuleValidator].
     */
    fun rule(draft: Draft): ParserRule {
        val statement = draft.kind == RuleKind.STATEMENT
        val fields = Field.of(draft.kind)
        val marks = draft.marks.filter { it.field in fields }
        val format = draft.dateFormat.takeIf { marks.any { it.field.isDate } }
        val rule = ParserRule(
            v = RuleFormat.VERSION,
            id = draft.id,
            bank = draft.bank.trim(),
            senders = draft.senders,
            pattern = pattern(draft.body, marks, format),
            kind = RuleKind.STATEMENT.takeIf { statement },
            direction = draft.direction.takeUnless { statement },
            accountType = draft.accountType.takeUnless { statement },
            dateFormat = format.takeUnless { statement },
            dueDateFormat = format.takeIf { statement }
        )
        return rule.copy(v = RuleFormat.versionFor(rule))
    }

    /**
     * The pattern for [body] with [marks] as named groups. Marks are trimmed of punctuation at
     * their ends (the `.` in `23-09-26.`), which stays literal. Overlapping marks keep the first.
     */
    fun pattern(body: String, marks: List<Mark>, dateFormat: String?): String {
        val spans = marks.mapNotNull { trim(body, it) }.sortedBy { it.start }
            .fold(mutableListOf<Mark>()) { kept, mark ->
                if (kept.lastOrNull()?.let { it.end > mark.start } != true) kept += mark
                kept
            }
        if (spans.isEmpty()) return ""
        val words = words(body)
        val first = spans.first().start
        val out =
            StringBuilder(PatternParts.literal(body.substring(contextStart(words, first), first)))
        spans.forEachIndexed { index, mark ->
            val next = spans.getOrNull(index + 1)?.start
            val until = next ?: contextEnd(words, mark.end)
            val text = body.substring(mark.start, mark.end)
            val last = next == null && until == mark.end
            out.append(PatternParts.group(text, mark.field, dateFormat, last))
            out.append(PatternParts.literal(body.substring(mark.end, until)))
        }
        return out.toString()
    }

    /**
     * A rule id from the bank and direction that isn't in [taken]: `hdfc-bank-debit`, then
     * `hdfc-bank-debit-2` and so on. A statement rule has no direction: `hdfc-bank-statement`.
     */
    fun ruleId(bank: String, direction: RuleDirection?, taken: Set<String>): String {
        val what = direction?.name?.lowercase(Locale.ROOT) ?: "statement"
        val slug = bank.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')
            .take(MAX_ID_BASE).trim('-').ifEmpty { "custom" }
        val base = "$slug-$what"
        return generateSequence(1) { it + 1 }
            .map { if (it == 1) base else "$base-$it" }
            .first { it !in taken }
    }

    /** A guess at the direction from the SMS words, checked in the engine's order. */
    fun guessDirection(body: String): RuleDirection {
        val lower = body.lowercase(Locale.ROOT)
        return listOf(
            RuleDirection.REFUND to listOf("refund", "reversed", "reversal"),
            RuleDirection.DEBIT to listOf("debited", "sent", "spent", "paid", "withdrawn"),
            RuleDirection.CREDIT to listOf("credited", "received", "deposited")
        ).firstOrNull { (_, words) -> words.any(lower::contains) }?.first ?: RuleDirection.DEBIT
    }

    /** A guess at the account type from the SMS words. */
    fun guessAccountType(body: String): RuleAccountType {
        val lower = body.lowercase(Locale.ROOT)
        return when {
            "credit card" in lower -> RuleAccountType.CREDIT_CARD
            "debit card" in lower -> RuleAccountType.DEBIT_CARD
            "wallet" in lower -> RuleAccountType.WALLET
            else -> RuleAccountType.BANK
        }
    }

    /** Whether [body] gets past [filters]' content checks, as an SMS no rule read must. */
    fun mayBeTransaction(body: String, filters: SmsFilters = SmsFilters()): Boolean =
        filters.contentReason(body) == null

    private fun trim(body: String, mark: Mark): Mark? {
        val start = mark.start.coerceIn(0, body.length)
        val text = body.substring(start, mark.end.coerceIn(start, body.length))
        val from = start + text.length - text.trimStart(::isEdge).length
        val until = start + text.trimEnd(::isEdge).length
        return if (from < until) mark.copy(start = from, end = until) else null
    }

    /** Where the literal text before the first mark starts: [WORDS_BEFORE] whole words back. */
    private fun contextStart(words: List<Word>, firstStart: Int): Int {
        val before = words.filter { it.start < firstStart }
        // A word the mark starts inside (the `(` of `(Rs.500`) counts as part of the mark's word.
        val whole = before.count { it.end <= firstStart }
        return before.getOrNull(whole - WORDS_BEFORE)?.start
            ?: before.firstOrNull()?.start
            ?: firstStart
    }

    /**
     * Where the literal text after the last mark ends: the rest of its word (the `.` of `26.`) and
     * the next word, so a payee in the middle knows where to stop.
     */
    private fun contextEnd(words: List<Word>, lastEnd: Int): Int =
        words.firstOrNull { it.start >= lastEnd }?.end
            ?: words.lastOrNull { it.end > lastEnd }?.end
            ?: lastEnd
}

/** [text] without spaces and punctuation at its ends: `23-09-26.` → `23-09-26`. */
internal fun trimEdges(text: String): String = text.trim(::isEdge)

private const val EDGE_PUNCTUATION = ".,;:!?()[]{}'\""

private fun isEdge(c: Char) = c.isWhitespace() || c in EDGE_PUNCTUATION
