package com.openhand.khata.sms.parser

import com.openhand.khata.sms.parser.RuleMaker.Field
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldGuesserTest {
    private fun text(body: String, field: Field): String? = FieldGuesser.guess(body)[field]?.text

    /** [body] gets exactly the [fields] guessed, as text, and [direction]. */
    private fun assertGuess(
        body: String,
        direction: RuleDirection?,
        vararg fields: Pair<Field, String>
    ) {
        val guesses = FieldGuesser.guess(body)
        assertEquals(body, fields.toMap(), guesses.fields.mapValues { it.value.text })
        assertEquals("direction in $body", direction, guesses.direction)
    }

    @Test
    fun kotakUpiSent() = assertGuess(
        "Sent Rs.366.00 from Kotak Bank A/c X7391 to SAMPLE INTERNATIONAL on 23-09-26. UPI Ref " +
            "408756022625. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud",
        direction = RuleDirection.DEBIT,
        Field.AMOUNT to "Rs.366.00",
        Field.PAYEE to "SAMPLE INTERNATIONAL",
        Field.ACCOUNT to "X7391",
        Field.REF to "408756022625",
        Field.DATE to "23-09-26"
    )

    @Test
    fun hdfcUpiDebit() = assertGuess(
        "Rs.450.00 debited from a/c **1234 on 11-10-26 to VPA swiggy@icici (UPI Ref No " +
            "512345678901). Not you? Call 18002586161",
        direction = RuleDirection.DEBIT,
        Field.AMOUNT to "Rs.450.00",
        Field.PAYEE to "swiggy@icici",
        Field.ACCOUNT to "**1234",
        Field.REF to "512345678901",
        Field.DATE to "11-10-26"
    )

    @Test
    fun iciciCardSpend() = assertGuess(
        "INR 1,234.00 spent using ICICI Bank Card XX5678 on 10-Oct-26 at AMAZON. Avl Limit: " +
            "INR 50,000.00. If not you, call 1800 2662",
        direction = RuleDirection.DEBIT,
        Field.AMOUNT to "INR 1,234.00",
        Field.PAYEE to "AMAZON",
        Field.ACCOUNT to "XX5678",
        Field.DATE to "10-Oct-26"
    )

    @Test
    fun sbiUpiWithBareAmount() = assertGuess(
        "Dear UPI user A/C X4321 debited by 500.0 on date 10Oct26 trf to SWIGGY Refno " +
            "612345678901. If not u? call 1800111109. -SBI",
        direction = RuleDirection.DEBIT,
        Field.AMOUNT to "500.0",
        Field.PAYEE to "SWIGGY",
        Field.ACCOUNT to "X4321",
        Field.REF to "612345678901",
        Field.DATE to "10Oct26"
    )

    @Test
    fun axisCredit() = assertGuess(
        "INR 25,000.00 credited to A/c no. XX9876 on 01-10-2026 at 10:15:30 IST. Info: " +
            "SALARY ACME. Avl Bal: INR 1,25,000.00",
        direction = RuleDirection.CREDIT,
        Field.AMOUNT to "INR 25,000.00",
        Field.PAYEE to "SALARY ACME",
        Field.ACCOUNT to "XX9876",
        Field.DATE to "01-10-2026"
    )

    @Test
    fun atmWithdrawal() = assertGuess(
        "Rs.2,000 withdrawn at ATM using card XX5678 on 05-10-26. Avl bal Rs.12,000",
        direction = RuleDirection.DEBIT,
        Field.AMOUNT to "Rs.2,000",
        Field.PAYEE to "ATM",
        Field.ACCOUNT to "XX5678",
        Field.DATE to "05-10-26"
    )

    @Test
    fun neftCredit() = assertGuess(
        "Your a/c XX2468 is credited with INR 10,000.00 on 03/10/2026 from RAVI KUMAR by NEFT " +
            "UTR No HDFCN26276123456",
        direction = RuleDirection.CREDIT,
        Field.AMOUNT to "INR 10,000.00",
        Field.PAYEE to "RAVI KUMAR",
        Field.ACCOUNT to "XX2468",
        Field.REF to "HDFCN26276123456",
        Field.DATE to "03/10/2026"
    )

    @Test
    fun impsDebit() = assertGuess(
        "Rs 1500 sent via IMPS from Card ending 1357 to ANITA SHARMA on 12 Oct 2026. IMPS Ref " +
            "627812345678",
        direction = RuleDirection.DEBIT,
        Field.AMOUNT to "Rs 1500",
        Field.PAYEE to "ANITA SHARMA",
        Field.ACCOUNT to "1357",
        Field.REF to "627812345678",
        Field.DATE to "12 Oct 2026"
    )

    @Test
    fun refundWinsOverCredited() {
        val guesses = FieldGuesser.guess("Refund of Rs.499.00 credited to your credit card XX5678.")
        assertEquals(RuleDirection.REFUND, guesses.direction)
        assertEquals(RuleAccountType.CREDIT_CARD, guesses.accountType)
        assertEquals(49_900L, guesses.amountPaise)
    }

    @Test
    fun aBalanceIsNeverTheAmount() {
        listOf(
            "Avl bal Rs.12,000 in A/c XX1234. Rs.300 debited for BILL",
            "Your Available Balance is INR 5,000.00. Paid Rs 40 at CAFE",
            "Rs.500 debited from A/c XX1234. Avl Bal: Rs.12,000.50"
        ).forEach { body ->
            val amount = text(body, Field.AMOUNT)
            assertTrue(
                "$amount in $body",
                amount != null && "12,000" !in amount && "5,000" !in amount
            )
        }
        assertNull(text("Avl bal Rs.12,000 in A/c XX1234 as on today", Field.AMOUNT))
    }

    @Test
    fun noGuessWithoutClues() {
        val guesses = FieldGuesser.guess("Hello, your appointment is confirmed for tomorrow")
        assertEquals(emptyMap<Field, FieldGuesser.Guess>(), guesses.fields)
        assertNull(guesses.direction)
        assertNull(guesses.draft("x", "X", listOf("XX")))
    }

    @Test
    fun aDraftReadsItsOwnSms() {
        val body =
            "Received Rs.1.50 in your Kotak Bank AC 7391 from ANITA SHARMA on 10-09-26.UPI " +
                "Ref:702822213535"
        val draft =
            requireNotNull(
                FieldGuesser.guess(body).draft("kotak-credit", "Kotak", listOf("KOTAKB"))
            )
        val check = RuleValidator.validate(RuleMaker.rule(draft)) as RuleCheck.Valid
        val at = Instant.parse("2026-09-10T07:00:00Z").toEpochMilli()
        val read = SmsParser(listOf(check.rule), ZoneId.of("Asia/Kolkata"))
            .parse("JM-KOTAKB-S", body, at) as ParseResult.Parsed
        assertEquals(150L, read.sms.amountPaise)
        assertEquals("ANITA SHARMA", read.sms.payee)
        assertEquals("7391", read.sms.accountLast4)
        assertEquals("702822213535", read.sms.reference)
    }
}
