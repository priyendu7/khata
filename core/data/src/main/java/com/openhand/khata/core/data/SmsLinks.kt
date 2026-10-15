package com.openhand.khata.core.data

/**
 * Takes the links out of an SMS before it's kept (#154). A link to a statement can carry a token
 * for the user's account, and Khata never opens one.
 */
internal object SmsLinks {
    /**
     * `https://…`, `www.…`, or a domain with a path such as `axis.bank.in/ccpaynow`. The last
     * part of the domain must be letters, so `Rs.500.00/-` isn't a link.
     */
    private val LINK = Regex(
        """\b(?:https?://|www\.)\S+|\b[a-z0-9-]+(?:\.[a-z0-9-]+)*\.[a-z]{2,}/\S*""",
        RegexOption.IGNORE_CASE
    )

    fun strip(sms: String): String =
        sms.replace(LINK, "").lines().joinToString("\n") { it.trimEnd() }.trimEnd()
}
