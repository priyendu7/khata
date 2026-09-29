package com.openhand.khata.sms.parser

/**
 * Decides what happens to a bank SMS that no rule read: a message that clearly isn't a
 * transaction (an OTP, an offer, a reminder, a failed payment) is dropped; anything else goes to
 * the review inbox, so a new transaction format is noticed rather than lost.
 *
 * It only runs after every rule has failed, so it can never drop an SMS a rule read. That matters
 * because real transaction SMS mention these words too ("Never share card details/OTP").
 */
internal object NotTransactionFilter {
    private fun words(vararg patterns: String) =
        Regex(patterns.joinToString("|", prefix = "(?:", postfix = ")"), RegexOption.IGNORE_CASE)

    /** `Rs.500`, `INR 18.00`, `₹99`; the word boundary keeps "hours 5" from counting. */
    private val AMOUNT = words("""\b(?:rs\.?|inr)\s*\d""", """₹\s*\d""")

    private val NOT_TRANSACTION = listOf(
        // One-time passwords and verification codes.
        words("""\botp\b""", """\bone[- ]time password\b""", """\bverification code\b"""),
        // UPI collect requests: money was asked for, not moved.
        words("""\bhas requested (?:money|payment)\b""", """\bcollect request\b"""),
        // Bill, card and loan reminders.
        words(
            """\bdue (?:date|on|by)\b""",
            """\b(?:minimum|total) (?:amount )?due\b""",
            """\bis due\b""",
            """\boverdue\b"""
        ),
        // Balance-only messages (a transaction SMS that also shows the balance is read by a rule).
        words(
            """\b(?:avl|available|avail|clear|ledger)\.? ?bal(?:ance)?\b""",
            """\bbalance (?:is|as on)\b"""
        ),
        // Mandates and standing instructions being set up.
        words("""\be-?mandate\b""", """\bstanding instruction\b"""),
        // Offers and promotions.
        words(
            """\bpre-?approved\b""",
            """\boffer\b""",
            """\bapply now\b""",
            """\beligible for\b""",
            """\blimited period\b"""
        )
    )

    /**
     * Failed payments, unless money has come back for one ("will be refunded" is only a promise,
     * so it doesn't count).
     */
    private val FAILED = words("""\bfailed\b""", """\bdeclined\b""", """\bunsuccessful\b""")
    private val MONEY_BACK = words(
        """\brefund of\b""",
        """\brefunded to\b""",
        """\bcredited back\b""",
        """\breversed\b""",
        """\breversal\b"""
    )

    fun isNotTransaction(body: String): Boolean = !AMOUNT.containsMatchIn(body) ||
        NOT_TRANSACTION.any { it.containsMatchIn(body) } ||
        FAILED.containsMatchIn(body) &&
        !MONEY_BACK.containsMatchIn(body)
}
