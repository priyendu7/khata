package com.openhand.khata.sms.parser

/**
 * The content checks for an SMS that no rule read (PRD feature 7): one with no amount, one with
 * no transaction word ([TransactionWords]), and one that is clearly something else (an OTP, an
 * offer, a reminder, a failed payment). Anything they let through goes to the review inbox, so a
 * new transaction format is noticed rather than lost.
 *
 * They only run after every rule has failed, so they can never drop an SMS a rule read. That
 * matters because real transaction SMS mention these words too ("Never share card details/OTP").
 */
internal object NotTransactionFilter {
    private fun words(vararg patterns: String) =
        Regex(patterns.joinToString("|", prefix = "(?:", postfix = ")"), RegexOption.IGNORE_CASE)

    /** `Rs.500`, `INR 18.00`, `₹99`; the word boundary keeps "hours 5" from counting. */
    private val AMOUNT = words("""\b(?:rs\.?|inr)\s*\d""", """₹\s*\d""")

    private val GROUPS = mapOf(
        NotTransactionGroup.OTP to
            words("""\botp\b""", """\bone[- ]time password\b""", """\bverification code\b"""),
        // Money was asked for, not moved.
        NotTransactionGroup.COLLECT_REQUEST to
            words("""\bhas requested (?:money|payment)\b""", """\bcollect request\b"""),
        NotTransactionGroup.REMINDER to words(
            """\bdue (?:date|on|by)\b""",
            """\b(?:minimum|total) (?:amount )?due\b""",
            """\bis due\b""",
            """\boverdue\b"""
        ),
        // A transaction SMS that also shows the balance is read by a rule.
        NotTransactionGroup.BALANCE to words(
            """\b(?:avl|available|avail|clear|ledger)\.? ?bal(?:ance)?\b""",
            """\bbalance (?:is|as on)\b"""
        ),
        NotTransactionGroup.MANDATE to words("""\be-?mandate\b""", """\bstanding instruction\b"""),
        NotTransactionGroup.OFFER to words(
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

    fun hasAmount(body: String): Boolean = AMOUNT.containsMatchIn(body)

    /** The first word group [body] matches, with the words it matched; null if none. */
    fun notTransaction(body: String): FilterReason.NotTransaction? {
        val (group, regex) = GROUPS.entries.firstOrNull { it.value.containsMatchIn(body) }
            ?.toPair()
            ?: (NotTransactionGroup.FAILED to FAILED).takeIf {
                FAILED.containsMatchIn(body) && !MONEY_BACK.containsMatchIn(body)
            }
            ?: return null
        val matched = regex.findAll(body).map { it.value }.distinct().toList()
        return FilterReason.NotTransaction(group, matched)
    }
}
