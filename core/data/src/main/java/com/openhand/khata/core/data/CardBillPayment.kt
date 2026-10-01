package com.openhand.khata.core.data

import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.SmsTransaction

/**
 * Spots a credit card bill payment in a bank SMS (PRD feature 1): the purchases on the card were
 * already counted, so paying the bill is a transfer, not more spending. Both sides count:
 * - money out of a bank account to CRED, to a credit card biller (often through BBPS), or with a
 *   text that says it's a credit card payment;
 * - money into a credit card, which is the card's "payment received" (a refund to a card is read
 *   as a refund by its rule).
 */
internal object CardBillPayment {
    fun matches(sms: SmsTransaction): Boolean = when {
        sms.accountType == AccountType.CREDIT_CARD -> sms.direction == Direction.CREDIT
        sms.direction != Direction.DEBIT -> false
        else -> sms.payee?.let(::isCardPayee) == true ||
            CARD_PAYMENT.containsMatchIn(sms.rawSms)
    }

    /** CRED by name or UPI ID (`cred.club@axisb`), or a biller named after a credit card. */
    private fun isCardPayee(payee: String): Boolean =
        CRED.containsMatchIn(payee.trim()) || CREDIT_CARD.containsMatchIn(payee)

    private val CRED = Regex("""^cred(?:$|[\s.@_-]|club)""", RegexOption.IGNORE_CASE)
    private val CREDIT_CARD = Regex("""\bcredit\s?card\b""", RegexOption.IGNORE_CASE)

    /**
     * "Credit card bill", "credit card payment", "CC bill payment", or BBPS with a card: never
     * "card" alone, which a debit card purchase also says.
     */
    private val CARD_PAYMENT = Regex(
        listOf(
            """\b(?:credit\s?card|cc)\b[^.]{0,20}?\b(?:bill|payment|dues?)\b""",
            """\bbbps\b.*\bcard\b""",
            """\bcard\b.*\bbbps\b"""
        ).joinToString("|"),
        RegexOption.IGNORE_CASE
    )
}
