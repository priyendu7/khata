package com.openhand.khata.sms.parser

/** The pieces [RuleMaker] builds a pattern from, all within the allowed subset ([RegexSubset]). */
internal object PatternParts {
    private val WHITESPACE = Regex("""\s+""")
    private val NUMBER = Regex("""\d[\d,]*(?:\.\d+)?""")
    private val AMOUNT_NUMBER = Regex("""\d[\d,]*(?:\.\d{1,2})?""")
    private val CURRENCY = Regex("""(?:rs\.?|inr|₹)\s*$""", RegexOption.IGNORE_CASE)
    private val LABEL = Regex("""^.*[:#=]""")
    private const val SPECIAL = """\.^$|?*+()[]{}"""

    /** The named group for a marked part [text]. [last]: nothing follows it in the pattern. */
    fun group(text: String, field: RuleMaker.Field, dateFormat: String?, last: Boolean): String {
        val name = field.group
        return when (field) {
            RuleMaker.Field.AMOUNT -> amount(text)
            // Lazy, so it stops at the text after it; greedy only at the very end of the SMS.
            RuleMaker.Field.PAYEE -> "(?<$name>" + (if (last) ".+" else ".+?") + ")"
            RuleMaker.Field.ACCOUNT -> labelled(text, "(?<$name>[^\\s\\d]*\\d{3,})")
            RuleMaker.Field.REF -> labelled(text, "(?<$name>[a-z0-9]+)")
            RuleMaker.Field.DATE ->
                "(?<$name>" + (dateFormat?.let(DateFormats::regex) ?: ".+?") + ")"
        }
    }

    /** [text] as it is: escaped, spaces as any whitespace, numbers as any number. */
    fun literal(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val space = WHITESPACE.matchAt(text, i)
            val number = NUMBER.matchAt(text, i)
            when {
                space != null -> {
                    out.append("\\s+")
                    i = space.range.last + 1
                }
                number != null -> {
                    out.append("\\d[\\d,]*(?:\\.\\d+)?")
                    i = number.range.last + 1
                }
                else -> {
                    if (text[i] in SPECIAL) out.append('\\')
                    out.append(text[i])
                    i++
                }
            }
        }
        return out.toString()
    }

    /**
     * `Rs.500/-` → a currency (any of `Rs.`, `Rs`, `INR`, `₹`), the amount group, then the `/-`
     * as literal text.
     */
    private fun amount(text: String): String {
        val group = "(?<${RuleFormat.AMOUNT}>[\\d,]+(?:\\.\\d{1,2})?)"
        val number = AMOUNT_NUMBER.find(text) ?: return group
        val before = text.substring(0, number.range.first)
        val prefix = CURRENCY.find(before)?.let { currency ->
            literal(before.substring(0, currency.range.first)) + "(?:rs\\.?|inr|₹)\\s*"
        } ?: literal(before)
        return prefix + group + literal(text.substring(number.range.last + 1))
    }

    /** `Ref:1234` → `Ref:` as literal text, then [group]. */
    private fun labelled(text: String, group: String): String =
        literal(LABEL.find(text)?.value.orEmpty()) + group
}
