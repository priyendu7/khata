package com.openhand.khata.core.data

import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.SmsTransaction

/**
 * Spots a credit card bill payment in a bank SMS (PRD feature 1): the purchases on the card were
 * already counted, so paying the bill is a transfer, not more spending. Both sides count:
 * - money out of a bank account to CRED, to a credit card biller (often through BBPS), or with a
 *   text that says it's a credit card payment;
 * - money into a credit card whose text says it's a payment ("payment received"). Cashback,
 *   refunds and reversals into a card are refunds instead ([isCardRefund], #166).
 */
internal object CardBillPayment {
    fun matches(sms: SmsTransaction): Boolean = when {
        isCardCredit(sms) -> !isCardRefund(sms) && PAYMENT_RECEIVED.containsMatchIn(sms.rawSms)
        sms.accountType == AccountType.CREDIT_CARD -> false
        sms.direction != Direction.DEBIT -> false
        else -> sms.payee?.let(::isCardPayee) == true ||
            CARD_PAYMENT.containsMatchIn(sms.rawSms)
    }

    /** Money into a credit card that's cashback, a refund or a reversal: a refund on the card. */
    fun isCardRefund(sms: SmsTransaction): Boolean =
        isCardCredit(sms) && CARD_REFUND.containsMatchIn(sms.rawSms)

    /**
     * Money into a credit card that's neither a payment nor a refund: it stays a credit on the
     * card, but waits in To review so the user can say what it was.
     */
    fun isUnclearCardCredit(sms: SmsTransaction): Boolean =
        isCardCredit(sms) && !isCardRefund(sms) && !matches(sms)

    private fun isCardCredit(sms: SmsTransaction) =
        sms.accountType == AccountType.CREDIT_CARD && sms.direction == Direction.CREDIT

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

    /**
     * A card's payment: "payment of Rs.X received", "payment … credited to your card",
     * "received towards your … card", or BBPS. Amounts have dots, so gaps allow any character.
     */
    private val PAYMENT_RECEIVED = Regex(
        listOf(
            """\bpayments?\b.{0,80}?\b(?:received|credited)\b""",
            """\breceived\b.{0,60}?\btowards\b.{0,40}?\bcard\b""",
            """\bbbps\b"""
        ).joinToString("|"),
        RegexOption.IGNORE_CASE
    )

    private val CARD_REFUND = Regex(
        """\b(?:cash\s?back|refund(?:ed)?|reversal|reversed)\b""",
        RegexOption.IGNORE_CASE
    )
}
