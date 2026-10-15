package com.openhand.khata.sms.parser

import java.util.Locale
import kotlin.math.abs

/**
 * Guesses where the amount, payee, account, reference and date are in a bank SMS (PRD feature 8),
 * or the card, total, minimum and due date in a card statement, so the rule maker starts with them
 * marked and the user only confirms. Plain patterns over the
 * shapes Indian bank SMS share: no network and no model, so the same answer on every phone.
 *
 * A balance or limit (`Avl bal Rs.12,000`) is never taken as the amount.
 */
object FieldGuesser {
    /** Characters [start] until [end] (exclusive) of the SMS are probably the field. */
    data class Guess(val start: Int, val end: Int, val text: String, val confidence: Float)

    /** The guess for each field found, and the direction and account type the words suggest. */
    data class Guesses(
        val body: String,
        val fields: Map<RuleMaker.Field, Guess>,
        /** The format that reads the guessed date (the due date, in a statement). */
        val dateFormat: String?,
        /** Null when the SMS has no transaction word. */
        val direction: RuleDirection?,
        val accountType: RuleAccountType,
        val kind: RuleKind = RuleKind.TRANSACTION
    ) {
        operator fun get(field: RuleMaker.Field): Guess? = fields[field]

        val marks: List<RuleMaker.Mark>
            get() = fields.map { (field, guess) -> RuleMaker.Mark(field, guess.start, guess.end) }

        val amountPaise: Long? get() = get(RuleMaker.Field.AMOUNT)?.text?.let(Fields::amountPaise)

        /**
         * A rule made from the guesses, as Make a parser would make it if every guess were
         * confirmed. Null without the fields the kind needs, or a transaction's direction.
         */
        fun draft(id: String, bank: String, senders: List<String>): RuleMaker.Draft? {
            val statement = kind == RuleKind.STATEMENT
            val direction = direction ?: RuleDirection.DEBIT.takeIf { statement }
            return if (direction == null ||
                !RuleMaker.Field.requiredFor(kind).all { it in fields }
            ) {
                null
            } else {
                RuleMaker.Draft(
                    id = id,
                    bank = bank,
                    senders = senders,
                    body = body,
                    marks = marks,
                    direction = direction,
                    accountType = accountType,
                    dateFormat = dateFormat,
                    kind = kind
                )
            }
        }
    }

    internal const val SURE = 0.9f
    private const val LIKELY = 0.7f
    private const val UNSURE = 0.5f

    /** How far before an amount a balance word counts, and after a word an amount is "near". */
    private const val BALANCE_WINDOW = 24
    private const val NEAR_WORD = 40
    private const val MIN_REF_LENGTH = 6

    internal val OPTIONS = setOf(RegexOption.IGNORE_CASE)
    internal const val WORD_START = """(?<![\p{L}\p{N}])"""
    internal const val NUMBER = """\d(?:[\d,]*\d)?(?:\.\d{1,2})?"""

    private val CURRENCY_AMOUNT =
        Regex("""$WORD_START(?:rs\.?|inr|₹)\s*$NUMBER(?!\d|\.\d)""", OPTIONS)

    /** `Paid 250 at`, `debited by 500.0 on`: a bare number right after a transaction word. */
    private val BARE_AMOUNT = Regex(
        """$WORD_START(?:debited|credited|paid|sent|spent|withdrawn|received)""" +
            """(?:\s+(?:by|with|of|for))?\s+($NUMBER)(?!\d|[/:\-.]\d)""",
        OPTIONS
    )

    /** Just before an amount, it's a balance or a limit: `Avl Bal: Rs.`, `limit is INR`. */
    private val BALANCE_BEFORE = Regex(
        """(?:bal\w*|limit|lmt|avl|available|avbl)[\s:.\-]*(?:is\s*)?$""",
        OPTIONS
    )

    private val LABELLED_ACCOUNT = Regex(
        """$WORD_START(?:a/c|acct|account|ac|card)(?:\s*(?:no|number)\.?)?""" +
            """(?:\s*ending(?:\s+(?:with|in))?)?[\s:.\-]*([x*]*\d{3,})(?!\d)""",
        OPTIONS
    )
    private val MASKED_ACCOUNT = Regex("""$WORD_START[x*]+\d{3,6}(?!\d)""", OPTIONS)

    private val REFERENCE = Regex(
        """$WORD_START(?:upi\s*ref(?:erence)?(?:\s*no)?|ref(?:erence)?(?:\s*(?:no|num|number))?""" +
            """|rrn|imps(?:\s*ref(?:\s*no)?)?|neft(?:\s*ref(?:\s*no)?)?|utr(?:\s*no)?""" +
            """|txn\s*id|transaction\s*id)[\s.:#\-]*(?:is\s*)?([a-z0-9]*\d[a-z0-9]*)""",
        OPTIONS
    )

    private const val TIME = """(?:,?\s*(?:at\s*)?\d{1,2}:\d{2}(?::\d{2})?)?"""
    internal const val DATE_TEXT =
        """(?<![\d\p{L}])(?:\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4}|\d{1,2}[- ]?[a-z]{3}[- ]?\d{2,4}""" +
            """|\d{4}-\d{2}-\d{2})(?![\d\p{L}])"""
    private val DATE = Regex("$DATE_TEXT$TIME", OPTIONS)

    private val TIME_AT_END = Regex("""$TIME$""", OPTIONS)

    private val PAYEE_WORD = Regex(
        """$WORD_START(to|from|at|towards|vpa|info:)(?:\s+vpa)?[\s:\-]+""",
        OPTIONS
    )

    /** Where a payee ends: ` on`, a full stop, a reference, a bracket, a balance… */
    private val PAYEE_END = Regex(
        """\s+on(?:\s|$)|\.(?:\s|$)|\s*\(|[;,]|\s+(?:upi\s*)?ref|\s+avl|\s+via\s|\s+using\s""" +
            """|\s+for\s|\s+by\s|\s+-|\s+if\s|\s+not\s|\s+bal|\s+(?:rs\.?|inr)\s|\s+₹""",
        OPTIONS
    )

    /** Not a payee: the user's own account or card, or the start of a sentence about them. */
    private val NOT_PAYEE = Regex(
        """^(?:your|you|a/c|ac|acct|account|card|the|us|registering|mobile)(?![\p{L}])""",
        OPTIONS
    )

    /** `… LTD Kotak Bank` at the very end: the bank signing off, not part of the payee. */
    private val BANK_SIGN_OFF = Regex("""\s+-?\s*\S+\s+bank$""", OPTIONS)

    private val DIRECTION_WORDS = listOf(
        RuleDirection.REFUND to listOf("refund", "reversed", "reversal"),
        RuleDirection.DEBIT to listOf(
            "debited",
            "spent",
            "sent",
            "paid",
            "withdrawn",
            "deducted",
            "purchase",
            "transferred"
        ),
        RuleDirection.CREDIT to listOf("credited", "received", "deposited")
    )
    private val DIRECTION = Regex(
        DIRECTION_WORDS.flatMap { it.second }.joinToString("|", prefix = "$WORD_START(?:", ")"),
        OPTIONS
    )

    /** The payee words worth looking at for each direction, the most telling first. */
    private val PAYEE_WORDS = mapOf(
        RuleDirection.DEBIT to listOf("to", "at", "vpa", "info:", "towards"),
        RuleDirection.CREDIT to listOf("from", "vpa", "info:", "towards"),
        RuleDirection.REFUND to listOf("from", "at", "towards", "info:", "to")
    )

    /**
     * The guesses for [kind]: a card statement's fields ([StatementGuesser]) when the SMS looks
     * like one, otherwise a transaction's.
     */
    fun guess(
        body: String,
        kind: RuleKind = if (StatementGuesser.looksLikeStatement(body)) {
            RuleKind.STATEMENT
        } else {
            RuleKind.TRANSACTION
        }
    ): Guesses {
        if (kind == RuleKind.STATEMENT) return StatementGuesser.guess(body)
        val direction = direction(body)
        val fields = mutableMapOf<RuleMaker.Field, Guess>()
        fun add(field: RuleMaker.Field, guess: Guess?) {
            if (guess != null && fields.values.none { it.overlaps(guess) }) fields[field] = guess
        }
        add(RuleMaker.Field.AMOUNT, amount(body))
        add(RuleMaker.Field.ACCOUNT, account(body))
        add(RuleMaker.Field.REF, reference(body))
        val date = date(body, fields.values)
        add(RuleMaker.Field.DATE, date?.first)
        add(RuleMaker.Field.PAYEE, payee(body, direction, fields.values))
        return Guesses(
            body = body,
            fields = fields,
            dateFormat = date?.second?.takeIf { RuleMaker.Field.DATE in fields },
            direction = direction,
            accountType = RuleMaker.guessAccountType(body)
        )
    }

    /** The first transaction word decides, but a refund is a refund even if it says "credited". */
    private fun direction(body: String): RuleDirection? {
        val words = DIRECTION.findAll(body).map { it.value.lowercase(Locale.ROOT) }.toList()
        val first = words.firstOrNull()
        return when {
            words.any { it in DIRECTION_WORDS.first().second } -> RuleDirection.REFUND
            first == null -> null
            else -> DIRECTION_WORDS.first { (_, list) -> first in list }.first
        }
    }

    /** The amount nearest a transaction word, never one after "Avl bal" or "limit". */
    private fun amount(body: String): Guess? {
        val words = DIRECTION.findAll(body).map { it.range.first }.toList()
        val amounts = CURRENCY_AMOUNT.findAll(body).filterNot { match ->
            val before = body.substring(
                maxOf(0, match.range.first - BALANCE_WINDOW),
                match.range.first
            )
            BALANCE_BEFORE.containsMatchIn(before)
        }.toList()
        val nearest = amounts.minByOrNull { match ->
            words.minOfOrNull { abs(it - match.range.first) } ?: 0
        }
        if (nearest != null) {
            val near = words.any { abs(it - nearest.range.first) <= NEAR_WORD }
            return nearest.toGuess(if (near) SURE else LIKELY)
        }
        return BARE_AMOUNT.find(body)?.groups?.get(1)?.let { group ->
            Guess(group.range.first, group.range.last + 1, group.value, UNSURE)
        }
    }

    internal fun account(body: String): Guess? =
        LABELLED_ACCOUNT.find(body)?.groups?.get(1)?.let { group ->
            Guess(group.range.first, group.range.last + 1, group.value, SURE)
        } ?: MASKED_ACCOUNT.find(body)?.toGuess(LIKELY)

    private fun reference(body: String): Guess? = REFERENCE.findAll(body)
        .mapNotNull { it.groups[1] }
        .firstOrNull { it.value.length >= MIN_REF_LENGTH }
        ?.let { Guess(it.range.first, it.range.last + 1, it.value, SURE) }

    /** The first date a common format reads, with that format; with its time when it reads that. */
    private fun date(body: String, taken: Collection<Guess>): Pair<Guess, String>? =
        DATE.findAll(body).mapNotNull { match ->
            val guess = match.toGuess(SURE)
            if (taken.any { it.overlaps(guess) }) return@mapNotNull null
            DateFormats.matching(guess.text).firstOrNull()?.let { return@mapNotNull guess to it }
            val dateOnly = guess.text.replace(TIME_AT_END, "")
            DateFormats.matching(dateOnly).firstOrNull()?.let { format ->
                Guess(guess.start, guess.start + dateOnly.length, dateOnly, LIKELY) to format
            }
        }.firstOrNull()

    /**
     * The words after `to`, `from`, `at`, `VPA`… up to ` on`, a full stop or a reference. Which
     * of those words count depends on the direction: money sent goes *to* someone.
     */
    private fun payee(body: String, direction: RuleDirection?, taken: Collection<Guess>): Guess? {
        val preferred = PAYEE_WORDS[direction ?: RuleDirection.DEBIT].orEmpty()
        return PAYEE_WORD.findAll(body)
            .mapNotNull { match ->
                val word = match.groupValues[1].lowercase(Locale.ROOT)
                payeeAfter(body, match.range.last + 1, taken)?.let { word to it }
            }
            .filter { (word, _) -> word in preferred }
            .minByOrNull { (word, guess) -> preferred.indexOf(word) * body.length + guess.start }
            ?.second
    }

    private fun payeeAfter(body: String, start: Int, taken: Collection<Guess>): Guess? {
        val nextTaken = taken.filter { it.start >= start }.minOfOrNull { it.start } ?: body.length
        val stop = PAYEE_END.find(body, start)?.range?.first ?: body.length
        var end = minOf(stop, nextTaken)
        var text = body.substring(start, end).trimEnd()
        if (end == body.length) text = text.replace(BANK_SIGN_OFF, "")
        end = start + text.length
        val guess = Guess(start, end, text, LIKELY)
        return guess.takeIf {
            text.any(Char::isLetter) &&
                !NOT_PAYEE.containsMatchIn(text) &&
                taken.none { it.overlaps(guess) }
        }
    }

    private fun MatchResult.toGuess(confidence: Float) =
        Guess(range.first, range.last + 1, value, confidence)

    internal fun Guess.overlaps(other: Guess) = start < other.end && other.start < end
}
