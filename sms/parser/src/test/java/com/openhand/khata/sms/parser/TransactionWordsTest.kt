package com.openhand.khata.sms.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionWordsTest {
    @Test
    fun oneWordFromEachGroupPasses() {
        listOf(
            "Rs.120 spent at METRO",
            "Rs.500 credited to A/c XX1234",
            "Rs.75 paid via IMPS",
            "Txn of Rs.75 at SHOP",
            "आपके खाते में Rs.500 जमा किए गए",
            "Rs.500 डेबिट हुए"
        ).forEach { assertTrue(it, TransactionWords.found(it)) }
    }

    @Test
    fun wordStartsMatchLongerWords() {
        assertTrue(TransactionWords.found("Rs.120 DEBITED from A/c"))
        assertTrue(TransactionWords.found("Refunds of Rs.50 are on the way"))
    }

    @Test
    fun wholeWordsDontMatchInsideOtherWords() {
        assertTrue(TransactionWords.found("A/c XX1234 Dr. with Rs.300"))
        assertTrue(TransactionWords.found("A/c XX1234 Cr Rs.300"))
        assertTrue(TransactionWords.found("Rs.199 auto-debit set"))
        assertFalse(TransactionWords.found("Shipped to your address"))
        assertFalse(TransactionWords.found("Win Rs.1 crore"))
        assertFalse(TransactionWords.found("Download our SUPIMA app"))
    }

    @Test
    fun leftOutWordsDontCount() {
        listOf(
            "Pay Rs.499 now",
            "Payment of Rs.499 is pending",
            "Your balance is low",
            "Cashback reward offer of Rs.50",
            "123456 is the OTP for your transaction of Rs.10",
            "Amount INR 18 Rs 5"
        ).forEach { assertFalse(it, TransactionWords.found(it)) }
    }

    @Test
    fun aRuleThatReadsAnSmsWinsOverTheWordFilter() {
        val kotak = "Sent Rs.366.00 from Kotak Bank A/c X1234 to GENERAL STORE on 23-09-26. " +
            "UPI Ref 111122223333. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud"
        val parser = SmsParser(BuiltInRules.load())
        assertTrue(parser.parse("JM-KOTAKB-S", kotak, AT) is ParseResult.Parsed)
        // With the rules gone, the same SMS goes to review rather than being dropped.
        assertNull(SmsFilters().contentReason(kotak))
        assertEquals(
            ParseResult.Filtered(FilterReason.NoTransactionWord),
            parser.parse("JM-KOTAKB-S", "Rs.2,000 on hold on card XX5678.", AT)
        )
    }

    private companion object {
        const val AT = 1_790_000_000_000L
    }
}
