package com.openhand.khata.sms.parser

import com.openhand.khata.core.model.Money

internal object Fields {
    private const val LAST_DIGITS = 4
    private const val MIN_DIGITS = 3
    private val CURRENCY = Regex("""^(?:rs\.?|inr|₹)\s*""", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("""\s+""")

    /** `Rs.1,23,456.50`, `Rs 500`, `INR 1,000.00`, `₹99` or `250` → paise. Null if not positive. */
    fun amountPaise(text: String): Long? {
        val paise = Money.parsePaise(text.trim().replace(CURRENCY, ""))
        return paise?.takeIf { it > 0 }
    }

    /** `XX1234`, `*1234`, `A/c 91234` → the last 4 digits (or 3 when a bank shows only 3). */
    fun last4(text: String): String? {
        val digits = text.filter(Char::isDigit).takeLast(LAST_DIGITS)
        return digits.takeIf { it.length >= MIN_DIGITS }
    }

    /** Runs of whitespace collapsed, trimmed, and trailing punctuation dropped. */
    fun clean(text: String): String? {
        val cleaned = text.replace(WHITESPACE, " ").trim().trimEnd('.', ',', ';', ':').trim()
        return cleaned.takeIf { it.isNotEmpty() }
    }
}
