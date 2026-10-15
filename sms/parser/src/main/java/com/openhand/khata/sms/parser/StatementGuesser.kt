package com.openhand.khata.sms.parser

import com.openhand.khata.sms.parser.FieldGuesser.DATE_TEXT
import com.openhand.khata.sms.parser.FieldGuesser.NUMBER
import com.openhand.khata.sms.parser.FieldGuesser.OPTIONS
import com.openhand.khata.sms.parser.FieldGuesser.SURE
import com.openhand.khata.sms.parser.FieldGuesser.WORD_START
import com.openhand.khata.sms.parser.FieldGuesser.overlaps

/**
 * [FieldGuesser] for a credit card statement (#153): the card, then the total and minimum told
 * apart by their labels (never by order), then the due date.
 */
object StatementGuesser {
    /** A statement says "statement" and labels its total: never a bare amount. */
    private val STATEMENT_WORD = Regex("""$WORD_START(?:statement|stmt)\b""", OPTIONS)

    /** `Rs.`, `INR  Dr.` and the like, then the number: the money after a statement label. */
    private const val LABELLED_MONEY =
        """(?:\s*(?:of|due|amt|amount))*[\s:.\-]*((?:rs\.?|inr|₹)\s*(?:dr\.?\s*)?$NUMBER)"""
    private val TOTAL = Regex("""$WORD_START(?:total|tot)\b$LABELLED_MONEY""", OPTIONS)
    private val MIN_DUE = Regex("""$WORD_START(?:minimum|min)\b\.?$LABELLED_MONEY""", OPTIONS)
    private val DUE_DATE = Regex(
        """$WORD_START(?:due\s+(?:by|on|date)|pay\s+by|payment\s+due\s+date)\b""" +
            """[\s:\-]*($DATE_TEXT)""",
        OPTIONS
    )

    /** A card statement: the word "statement" and a labelled total. */
    fun looksLikeStatement(body: String): Boolean =
        STATEMENT_WORD.containsMatchIn(body) && TOTAL.containsMatchIn(body)

    /** The direction is null: a statement moves no money. */
    internal fun guess(body: String): FieldGuesser.Guesses {
        val fields = mutableMapOf<RuleMaker.Field, FieldGuesser.Guess>()
        fun add(field: RuleMaker.Field, guess: FieldGuesser.Guess?) {
            if (guess != null && fields.values.none { it.overlaps(guess) }) fields[field] = guess
        }
        add(RuleMaker.Field.ACCOUNT, FieldGuesser.account(body))
        add(RuleMaker.Field.TOTAL, labelled(TOTAL, body))
        add(RuleMaker.Field.MIN_DUE, labelled(MIN_DUE, body))
        val due = labelled(DUE_DATE, body)
        val format = due?.let { DateFormats.matching(it.text).firstOrNull() }
        if (format != null) add(RuleMaker.Field.DUE_DATE, due)
        return FieldGuesser.Guesses(
            body = body,
            fields = fields,
            dateFormat = format.takeIf { RuleMaker.Field.DUE_DATE in fields },
            direction = null,
            accountType = RuleAccountType.CREDIT_CARD,
            kind = RuleKind.STATEMENT
        )
    }

    /** [regex]'s first group, where it first matches. */
    private fun labelled(regex: Regex, body: String): FieldGuesser.Guess? =
        regex.find(body)?.groups?.get(1)?.let {
            FieldGuesser.Guess(it.range.first, it.range.last + 1, it.value, SURE)
        }
}
