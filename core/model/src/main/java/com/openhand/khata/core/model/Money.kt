package com.openhand.khata.core.model

import kotlin.math.abs

/**
 * Amounts are stored as whole paise (₹1 = 100 paise) in a [Long], so no floating-point maths ever
 * touches money. These helpers convert between paise and the text people type and read.
 */
object Money {
    const val PAISE_PER_RUPEE = 100L

    /** Most rupee digits accepted before the decimal point (up to ₹99,99,99,99,999). */
    const val MAX_RUPEE_DIGITS = 11

    private const val MAX_PAISE_DIGITS = 2
    private const val FIRST_GROUP = 3
    private const val GROUP = 2
    private val DIGITS = '0'..'9'

    /**
     * Parses typed text such as `250`, `1,250.5` or `99.99` into paise. Returns null for anything
     * that isn't a plain positive amount with at most two decimal places.
     */
    fun parsePaise(text: String): Long? {
        val clean = text.trim().replace(",", "")
        val parts = clean.split('.')
        val rupees = parts[0]
        val paise = parts.getOrNull(1).orEmpty()
        val valid = parts.size <= 2 &&
            (rupees.isNotEmpty() || paise.isNotEmpty()) &&
            rupees.length <= MAX_RUPEE_DIGITS &&
            paise.length <= MAX_PAISE_DIGITS &&
            rupees.all { it in DIGITS } &&
            paise.all { it in DIGITS }
        if (!valid) return null
        return (rupees.ifEmpty { "0" }.toLong() * PAISE_PER_RUPEE) + paise.padEnd(2, '0').toLong()
    }

    /** Paise as editable text: `25000` → `250`, `12345` → `123.45`, `12340` → `123.40`. */
    fun toInput(paise: Long): String {
        val rupees = paise / PAISE_PER_RUPEE
        val rest = paise % PAISE_PER_RUPEE
        return if (rest == 0L) "$rupees" else "$rupees.${rest.toString().padStart(2, '0')}"
    }

    /**
     * The one formatter for amounts shown anywhere in the app: Indian digit grouping with the rupee
     * sign, e.g. `₹1,00,000`, `₹1,234.50` or `-₹250`. Whole amounts drop `.00`.
     */
    fun format(paise: Long): String {
        // Quotient and remainder separately, so even Long.MIN_VALUE has no overflow on negation.
        val rupees = groupIndian(abs(paise / PAISE_PER_RUPEE).toString())
        val rest = abs(paise % PAISE_PER_RUPEE)
        val sign = if (paise < 0) "-" else ""
        val decimals = if (rest == 0L) "" else "." + rest.toString().padStart(2, '0')
        return "$sign₹$rupees$decimals"
    }

    /** `1234567` → `12,34,567`: the last three digits, then groups of two. */
    private fun groupIndian(digits: String): String {
        if (digits.length <= FIRST_GROUP) return digits
        val head = digits.dropLast(FIRST_GROUP)
        val tail = digits.takeLast(FIRST_GROUP)
        val groups = head.reversed().chunked(GROUP).joinToString(",").reversed()
        return "$groups,$tail"
    }
}
