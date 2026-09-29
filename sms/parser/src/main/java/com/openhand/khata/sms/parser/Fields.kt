package com.openhand.khata.sms.parser

import com.openhand.khata.core.model.Money

/**
 * An SMS sender as Indian operators deliver it: `AX-KOTAKB-S` is operator/region prefix `AX`
 * (it varies), header `KOTAKB` and TRAI category suffix `S` (service). `T` is transactional, `G`
 * government and `P` promotional. Some phones show only the header.
 */
data class SenderId(val header: String, val promotional: Boolean) {
    companion object {
        private const val PREFIX_LENGTH = 2
        private val CATEGORIES = setOf("S", "T", "P", "G")
        private val HEADER = Regex("[A-Z0-9]{3,9}")

        /** Null for anything that isn't a header, such as a phone number. */
        fun parse(sender: String): SenderId? {
            val parts = sender.trim().uppercase().split('-').toMutableList()
            val category = if (parts.size > 1 &&
                parts.last() in CATEGORIES
            ) {
                parts.removeAt(parts.lastIndex)
            } else {
                null
            }
            if (parts.size > 1 && parts.first().length == PREFIX_LENGTH) parts.removeAt(0)
            // Phone numbers (+919876543210, 9876543210) are people, not banks.
            val header = parts.singleOrNull()?.takeIf {
                HEADER.matches(it) && !it.all(Char::isDigit)
            }
            return header?.let { SenderId(it, promotional = category == "P") }
        }
    }
}

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
