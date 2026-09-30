package com.openhand.khata.sms.parser

import com.openhand.khata.core.model.Direction
import com.openhand.khata.sms.parser.RuleMaker.Field
import com.openhand.khata.sms.parser.RuleMaker.Mark
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMakerTest {
    private val zone = ZoneOffset.ofHoursMinutes(5, 30)
    private val received = LocalDate.of(2026, 9, 25).atTime(10, 0).atZone(zone)
        .toInstant().toEpochMilli()

    private val upi = "Sent Rs.366.00 from Kotak Bank A/c X1234 to GENERAL STORE on 23-09-26. " +
        "UPI Ref 111122223333. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud"

    /** Marks the first place [text] appears in [body]. */
    private fun mark(body: String, field: Field, text: String): Mark {
        val start = body.indexOf(text)
        check(start >= 0) { "$text not in $body" }
        return Mark(field, start, start + text.length)
    }

    private fun draft(
        body: String,
        marks: List<Mark>,
        dateFormat: String? = null,
        senders: List<String> = listOf("KOTAKB")
    ) = RuleMaker.Draft(
        id = "kotak-debit",
        bank = "Kotak",
        senders = senders,
        body = body,
        marks = marks,
        direction = RuleDirection.DEBIT,
        accountType = RuleAccountType.BANK,
        dateFormat = dateFormat
    )

    private fun compiled(draft: RuleMaker.Draft): CompiledRule {
        val check = RuleValidator.validate(RuleMaker.rule(draft))
        assertTrue("$check for ${RuleMaker.rule(draft).pattern}", check is RuleCheck.Valid)
        return (check as RuleCheck.Valid).rule
    }

    private fun parse(rule: CompiledRule, body: String): ParsedSms? =
        (SmsParser(listOf(rule), zone).parse("AX-KOTAKB-S", body, received) as? ParseResult.Parsed)
            ?.sms

    @Test
    fun markedFieldsAreReadBackFromTheSameSms() {
        val marks = listOf(
            mark(upi, Field.AMOUNT, "Rs.366.00"),
            mark(upi, Field.ACCOUNT, "X1234"),
            mark(upi, Field.PAYEE, "GENERAL STORE"),
            // Marked with its full stop, as tapping the word gives it.
            mark(upi, Field.DATE, "23-09-26."),
            mark(upi, Field.REF, "111122223333.")
        )
        val sms = parse(compiled(draft(upi, marks, "dd-MM-yy")), upi)

        assertNotNull(sms)
        sms!!
        assertEquals(36_600L, sms.amountPaise)
        assertEquals("1234", sms.accountLast4)
        assertEquals("GENERAL STORE", sms.payee)
        assertEquals("111122223333", sms.reference)
        assertEquals(Direction.DEBIT, sms.direction)
        assertEquals(
            LocalDate.of(2026, 9, 23),
            java.time.Instant.ofEpochMilli(sms.timestamp).atZone(zone).toLocalDate()
        )
    }

    @Test
    fun theRuleReadsOtherSmsOfTheSameKind() {
        val marks = listOf(
            mark(upi, Field.AMOUNT, "Rs.366.00"),
            mark(upi, Field.ACCOUNT, "X1234"),
            mark(upi, Field.PAYEE, "GENERAL STORE"),
            mark(upi, Field.REF, "111122223333")
        )
        val rule = compiled(draft(upi, marks))
        val other = "Sent Rs.1,20,000 from Kotak Bank A/c X9876 to Priya Traders on 01-10-26. " +
            "UPI Ref 999988887777. Not done by you?"

        val sms = parse(rule, other)!!

        assertEquals(12_000_000L, sms.amountPaise)
        assertEquals("9876", sms.accountLast4)
        assertEquals("Priya Traders", sms.payee)
        assertEquals("999988887777", sms.reference)
    }

    @Test
    fun theRuleDoesntReadADifferentKindOfSms() {
        val rule = compiled(draft(upi, listOf(mark(upi, Field.AMOUNT, "Rs.366.00"))))

        assertNull(parse(rule, "Received Rs.366.00 in your Kotak Bank AC X1234 from A B."))
    }

    @Test
    fun generatedPatternsPassTheValidatorAndStayInTheSubset() {
        val bodies = listOf(
            upi,
            "INR 2,500.00 debited from A/c no. XX5678 on 05-Sep-26 (UPI Ref No 123) [bal: 10.00]",
            "Rs.500/- spent on card *4321 at A+B STORE* on 2026-09-20 12:30:45. Ref#AB12C",
            "₹99 paid to {cafe} | ref=77 ^ \$ \\ end"
        )
        bodies.forEach { body ->
            // Everything between the first and last word stays literal text.
            val words = RuleMaker.words(body)
            val marks = listOf(
                Mark(Field.AMOUNT, words.first().start, words.first().end),
                Mark(Field.REF, words.last().start, words.last().end)
            )
            val pattern = RuleMaker.pattern(body, marks, null)

            assertNotNull(body, RegexSubset.namedGroups(pattern))
            val rule = RuleMaker.rule(draft(body, marks))
            assertTrue(pattern, RuleValidator.validate(rule) is RuleCheck.Valid)
        }
    }

    @Test
    fun textAroundTheMarkedPartsIsEscaped() {
        val body = "Paid Rs.50 at A+B (STORE) [x*y] ok? to CAFE."
        val marks = listOf(mark(body, Field.AMOUNT, "Rs.50"), mark(body, Field.PAYEE, "CAFE."))

        val pattern = RuleMaker.pattern(body, marks, null)

        assertTrue(pattern, pattern.contains("""A\+B\s+\(STORE\)\s+\[x\*y\]\s+ok\?"""))
        val rule = compiled(draft(body, marks))
        assertEquals("CAFE", parse(rule, body)!!.payee)
        // An unescaped `+` or `*` would let this through.
        assertNull(parse(rule, "Paid Rs.50 at AAB (STORE) [xxy] ok to CAFE."))
    }

    @Test
    fun theAmountAcceptsAnyCurrencyAndKeepsWhatFollowsIt() {
        val body = "Rs.500/- debited from your account for BILLDESK."
        val marks =
            listOf(mark(body, Field.AMOUNT, "Rs.500/-"), mark(body, Field.PAYEE, "BILLDESK"))
        val rule = compiled(draft(body, marks))

        assertEquals(50_000L, parse(rule, body)!!.amountPaise)
        val inr = parse(rule, "INR 1,250.50/- debited from your account for ELECTRICITY.")!!
        assertEquals(125_050L, inr.amountPaise)
        assertEquals("ELECTRICITY", inr.payee)
    }

    @Test
    fun numbersAndSpacesInTheTextAroundAreLoose() {
        val body = "Rs.200 spent. Avl bal Rs.1,234.00 at 10:05 on card XX1111."
        val marks = listOf(mark(body, Field.AMOUNT, "Rs.200"), mark(body, Field.ACCOUNT, "XX1111"))
        val rule = compiled(draft(body, marks))

        val sms = parse(rule, "Rs.75 spent.  Avl bal Rs.98,000.50 at 9:41 on card XXXXXX2222.")!!

        assertEquals(7_500L, sms.amountPaise)
        assertEquals("2222", sms.accountLast4)
    }

    @Test
    fun aLabelInsideAMarkedWordStaysLiteral() {
        val body = "Rs.10 paid from A/c:X4444 Ref:ABC123 thanks"
        val marks = listOf(
            mark(body, Field.AMOUNT, "Rs.10"),
            mark(body, Field.ACCOUNT, "A/c:X4444"),
            mark(body, Field.REF, "Ref:ABC123")
        )

        val sms = parse(compiled(draft(body, marks)), body)!!

        assertEquals("4444", sms.accountLast4)
        assertEquals("ABC123", sms.reference)
    }

    @Test
    fun aPayeeAtTheEndOfTheSmsTakesTheRest() {
        val body = "Rs.10 paid to SHARMA GENERAL STORE"
        val marks =
            listOf(
                mark(body, Field.AMOUNT, "Rs.10"),
                mark(body, Field.PAYEE, "SHARMA GENERAL STORE")
            )

        assertEquals("SHARMA GENERAL STORE", parse(compiled(draft(body, marks)), body)!!.payee)
    }

    @Test
    fun onlyAFewWordsBeforeTheFirstMarkAreKept() {
        val body = "Dear RAHUL, your a/c was debited Rs.10 today"
        val pattern = RuleMaker.pattern(body, listOf(mark(body, Field.AMOUNT, "Rs.10")), null)

        assertTrue(pattern, pattern.startsWith("was\\s+debited\\s+"))
    }

    @Test
    fun theDateRegexFollowsTheFormat() {
        assertEquals("""\d{2}-[a-z]{3}-\d{2}""", DateFormats.regex("dd-MMM-yy"))
        assertEquals("""\d{4}/\d{2}/\d{2}\s+\d{2}:\d{2}""", DateFormats.regex("yyyy/MM/dd HH:mm"))
        assertEquals("""\d{1,2}\s+at\s+\d{1,2}\s+[ap]m""", DateFormats.regex("d 'at' h a"))
        assertNull(DateFormats.regex("dd-MM-yy G"))
    }

    @Test
    fun everyOfferedDateFormatReadsWhatItsRegexMatches() {
        val samples = mapOf(
            "dd-MM-yy" to "23-09-26",
            "dd-MM-yyyy" to "23-09-2026",
            "dd/MM/yy" to "23/09/26",
            "dd/MM/yyyy" to "23/09/2026",
            "dd-MMM-yy" to "23-Sep-26",
            "dd-MMM-yyyy" to "23-SEP-2026",
            "dd MMM yyyy" to "23 Sep 2026",
            "ddMMMyy" to "23Sep26",
            "yyyy-MM-dd" to "2026-09-23",
            "dd-MM-yyyy HH:mm:ss" to "23-09-2026 14:05:09",
            "dd-MMM-yy HH:mm" to "23-Sep-26 14:05",
            "yyyy-MM-dd HH:mm:ss" to "2026-09-23 14:05:09"
        )
        assertEquals(DateFormats.COMMON.toSet(), samples.keys)
        samples.forEach { (format, text) ->
            val regex = Regex(DateFormats.regex(format)!!, RegexOption.IGNORE_CASE)
            assertTrue(format, regex.matches(text))
            assertTrue(format, format in DateFormats.matching(text))
        }
    }

    @Test
    fun dateFormatsAreOfferedOnlyWhenTheyReadTheMarkedText() {
        assertEquals(listOf("dd-MM-yy"), DateFormats.matching("23-09-26."))
        assertEquals(listOf("dd-MMM-yyyy"), DateFormats.matching("05-Sep-2026"))
        assertTrue(DateFormats.matching("GENERAL").isEmpty())
    }

    @Test
    fun ruleIdsComeFromTheBankAndDirectionAndDontClash() {
        assertEquals(
            "hdfc-bank-debit",
            RuleMaker.ruleId("HDFC Bank", RuleDirection.DEBIT, emptySet())
        )
        assertEquals(
            "hdfc-bank-debit-3",
            RuleMaker.ruleId(
                "HDFC Bank",
                RuleDirection.DEBIT,
                setOf("hdfc-bank-debit", "hdfc-bank-debit-2")
            )
        )
        assertEquals("custom-credit", RuleMaker.ruleId("₹₹", RuleDirection.CREDIT, emptySet()))
    }

    @Test
    fun guessesTheDirectionAndAccountType() {
        assertEquals(RuleDirection.DEBIT, RuleMaker.guessDirection(upi))
        assertEquals(
            RuleDirection.CREDIT,
            RuleMaker.guessDirection("Rs.5 credited to a/c XX1 from A")
        )
        assertEquals(
            RuleDirection.REFUND,
            RuleMaker.guessDirection("Refund of Rs.5 credited to your card")
        )
        assertEquals(
            RuleAccountType.CREDIT_CARD,
            RuleMaker.guessAccountType("Rs.5 spent on your Credit Card XX12")
        )
        assertEquals(RuleAccountType.BANK, RuleMaker.guessAccountType(upi))
    }

    @Test
    fun anSmsWithoutAnAmountOrWithAnOtpIsNotOffered() {
        assertTrue(RuleMaker.mayBeTransaction(upi))
        assertFalse(RuleMaker.mayBeTransaction("123456 is your OTP for Rs.500 at STORE"))
        assertFalse(RuleMaker.mayBeTransaction("Biometric authentication is enabled"))
    }

    @Test
    fun noMarksGiveNoPatternAndOverlappingMarksKeepTheFirst() {
        assertEquals("", RuleMaker.pattern(upi, emptyList(), null))

        val amount = mark(upi, Field.AMOUNT, "Rs.366.00")
        val overlapping = Mark(Field.PAYEE, amount.start + 2, amount.end + 5)
        val pattern = RuleMaker.pattern(upi, listOf(amount, overlapping), null)

        assertFalse(pattern, pattern.contains("payee"))
    }
}
