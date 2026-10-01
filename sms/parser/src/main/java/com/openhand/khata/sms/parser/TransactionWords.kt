package com.openhand.khata.sms.parser

/**
 * The words the "no transaction word" filter looks for (PRD feature 7). An SMS no rule read must
 * have at least one, or it's dropped. Change the list here, with a test in `TransactionWordsTest`.
 *
 * Left out on purpose: amount, Rs and INR (the no-amount check covers them); payment and pay
 * ("pay now", "payment due"); balance (balance-only SMS); cashback, offer and reward (promotions;
 * a real cashback says "credited"); transaction (it's in OTP SMS, and "txn" is enough).
 */
internal object TransactionWords {
    /** Matched at the start of a word, ignoring case: "debit" also matches "debited". */
    val WORD_STARTS = listOf(
        // Money going out.
        "debit", "debited", "spent", "paid", "sent", "withdrawn", "withdrawal", "purchase",
        "purchased", "deducted", "charged", "used", "transferred", "transfer",
        // Money coming in.
        "credit", "credited", "received", "deposited", "deposit", "added", "refund", "refunded",
        "reversed", "reversal"
    )

    /** Matched as whole words only, so "dr" doesn't match "address" or "cr" "crore". */
    val WHOLE_WORDS = listOf(
        // Payment methods.
        "UPI", "IMPS", "NEFT", "RTGS", "NACH", "ECS", "autopay", "auto-debit",
        // Abbreviations.
        "txn", "trf", "tfr", "Dr", "Cr", "POS"
    )

    /**
     * Hindi, matched as plain text: `\b` and letter classes don't work reliably on Devanagari
     * vowel signs.
     */
    val HINDI = listOf(
        "डेबिट", // debit
        "क्रेडिट", // credit
        "जमा", // deposited
        "निकासी", // withdrawal
        "भुगतान", // payment
        "प्राप्त", // received
        "भेजे" // sent
    )

    private const val NOT_IN_WORD = """[\p{L}\p{N}]"""
    private val STARTS = Regex(
        WORD_STARTS.joinToString("|", prefix = "(?<!$NOT_IN_WORD)(?:", postfix = ")") {
            Regex.escape(it)
        },
        RegexOption.IGNORE_CASE
    )
    private val WHOLE = Regex(
        WHOLE_WORDS.joinToString(
            "|",
            prefix = "(?<!$NOT_IN_WORD)(?:",
            postfix = ")(?!$NOT_IN_WORD)"
        ) { Regex.escape(it) },
        RegexOption.IGNORE_CASE
    )

    fun found(body: String): Boolean =
        STARTS.containsMatchIn(body) || WHOLE.containsMatchIn(body) || HINDI.any(body::contains)
}
