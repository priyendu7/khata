package com.openhand.khata.core.data

import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.SmsTransaction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardBillPaymentTest {
    private fun sms(
        body: String,
        payee: String? = null,
        direction: Direction = Direction.DEBIT,
        type: AccountType = AccountType.BANK
    ) = SmsTransaction(
        amountPaise = 100_000,
        direction = direction,
        timestamp = 0,
        bank = "Kotak",
        accountType = type,
        accountLast4 = "7391",
        payee = payee,
        referenceNo = null,
        rawSms = body
    )

    @Test
    fun credByNameOrUpiIdIsACardPayment() {
        listOf("CRED", "cred", "CRED Club", "cred.club@axisb", "CRED@ybl").forEach {
            assertTrue(it, CardBillPayment.matches(sms("Sent Rs.1000 to $it", payee = it)))
        }
    }

    @Test
    fun namesThatOnlyStartLikeCredAreNot() {
        listOf("CREDENCE STORES", "Credible Traders").forEach {
            assertFalse(it, CardBillPayment.matches(sms("Sent Rs.1000 to $it", payee = it)))
        }
    }

    @Test
    fun aCreditCardBillerOrTextIsACardPayment() {
        assertTrue(CardBillPayment.matches(sms("Sent Rs.1000", payee = "HDFC CREDIT CARD")))
        assertTrue(CardBillPayment.matches(sms("Rs.1000 paid towards your Credit Card bill")))
        assertTrue(CardBillPayment.matches(sms("Rs.1000 debited for CC bill payment")))
        assertTrue(CardBillPayment.matches(sms("BBPS txn of Rs.1000 for SBI Card successful")))
    }

    @Test
    fun aDebitCardPurchaseIsNot() {
        assertFalse(CardBillPayment.matches(sms("Debit card payment of Rs.1000 at AMAZON")))
        assertFalse(CardBillPayment.matches(sms("Rs.1000 paid by card at DMART")))
    }

    @Test
    fun moneyIntoACreditCardIsItsPaymentReceived() {
        val received = sms(
            "Payment of Rs.1000 received on your credit card",
            direction = Direction.CREDIT,
            type = AccountType.CREDIT_CARD
        )
        assertTrue(CardBillPayment.matches(received))
    }

    @Test
    fun aCardCreditIsAPaymentOnlyWhenItSaysSo() {
        fun credit(body: String) =
            sms(body, direction = Direction.CREDIT, type = AccountType.CREDIT_CARD)
        listOf(
            "Payment of Rs.1000 received on your credit card XX1234",
            "We have received Rs.1,000.00 towards your ICICI Bank Credit Card XX1234",
            "Your payment of Rs.1000.50 has been credited to your card XX1234",
            "BBPS payment of Rs 1000 for card XX1234 successful"
        ).forEach { assertTrue(it, CardBillPayment.matches(credit(it))) }
        listOf(
            "Cashback of Rs.163 credited to your Axis Bank card XX1234",
            "Refund of Rs.163 from AMAZON credited to card XX1234",
            "Reversal of Rs.163 processed on card XX1234",
            "Your payment of Rs.163 was reversed on card XX1234",
            "Rs.163 credited to your card XX1234 by Flipkart"
        ).forEach { assertFalse(it, CardBillPayment.matches(credit(it))) }
    }

    @Test
    fun cashbackRefundsAndReversalsOnACardAreRefunds() {
        fun credit(body: String) =
            sms(body, direction = Direction.CREDIT, type = AccountType.CREDIT_CARD)
        listOf("Cashback of Rs.163", "Refund of Rs.163", "Reversal of Rs.163", "Rs.163 reversed")
            .forEach { assertTrue(it, CardBillPayment.isCardRefund(credit("$it on card XX1234"))) }
        val other = credit("Rs.163 credited to your card XX1234 by Flipkart")
        assertFalse(CardBillPayment.isCardRefund(other))
        assertTrue(CardBillPayment.isUnclearCardCredit(other))
        // Into a bank account, cashback is just income.
        assertFalse(
            CardBillPayment.isCardRefund(sms("Cashback of Rs.163", direction = Direction.CREDIT))
        )
    }

    @Test
    fun spendingOnACreditCardIsNot() {
        val spent = sms(
            "Rs.1000 spent on your credit card at CRED store",
            payee = "CRED",
            type = AccountType.CREDIT_CARD
        )
        assertFalse(CardBillPayment.matches(spent))
    }

    @Test
    fun moneyIntoABankAccountIsNot() {
        val credit = sms(
            "Rs.1000 credited from CRED cashback",
            payee = "CRED",
            direction = Direction.CREDIT
        )
        assertFalse(CardBillPayment.matches(credit))
    }
}
