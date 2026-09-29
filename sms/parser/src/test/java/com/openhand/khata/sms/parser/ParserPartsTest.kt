package com.openhand.khata.sms.parser

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserPartsTest {
    @Test
    fun amounts() {
        mapOf(
            "Rs.1,23,456.50" to 12_345_650L,
            "Rs 500" to 50_000L,
            "Rs. 500" to 50_000L,
            "INR 1,000.00" to 100_000L,
            "INR1000" to 100_000L,
            "₹99" to 9_900L,
            "₹ 99.5" to 9_950L,
            "rs.60.90" to 6_090L,
            " 250 " to 25_000L
        ).forEach { (text, paise) -> assertEquals(text, paise, Fields.amountPaise(text)) }
        listOf("Rs.0.00", "Rs.", "INR 1.234", "USD 5", "-Rs.5", "Rs.5.5.5", "")
            .forEach { assertNull(it, Fields.amountPaise(it)) }
    }

    @Test
    fun senders() {
        mapOf(
            "AX-KOTAKB-S" to SenderId("KOTAKB", promotional = false),
            "JM-KOTAKB-S" to SenderId("KOTAKB", promotional = false),
            "VM-HDFCBK-T" to SenderId("HDFCBK", promotional = false),
            "JD-HDFCBK-G" to SenderId("HDFCBK", promotional = false),
            "AD-HDFCBK" to SenderId("HDFCBK", promotional = false),
            "HDFCBK" to SenderId("HDFCBK", promotional = false),
            " ax-kotakb-s " to SenderId("KOTAKB", promotional = false),
            "VM-KOTAKB-P" to SenderId("KOTAKB", promotional = true)
        ).forEach { (sender, id) -> assertEquals(sender, id, SenderId.parse(sender)) }
        listOf("+919876543210", "9876543210", "AX-98765-S", "", "AB", "AX-KOTAK BANK-S", "A-B-C-D")
            .forEach { assertNull(it, SenderId.parse(it)) }
    }

    @Test
    fun accountAndText() {
        assertEquals("1234", Fields.last4("X1234"))
        assertEquals("1234", Fields.last4("XXXXXXXXX501234"))
        assertEquals("123", Fields.last4("XX123"))
        assertNull(Fields.last4("XX12"))
        assertEquals("GENERAL STORE", Fields.clean("  GENERAL  \n STORE. "))
        assertNull(Fields.clean(" . "))
    }

    @Test
    fun regexSubsetAllowsCommonSyntax() {
        val ok = listOf(
            """Sent (?<amount>Rs\.?\s?[\d,]+(?:\.\d{1,2})?) to (?<payee>.+?) on""",
            """(?:Paid|Sent) (?<amount>\d+)""",
            """[^\]]+ (?<amount>\d+?)""",
            """a{2,3}? \+\+ [+*?] (?<amount>\d+)"""
        )
        ok.forEach { assertNotNull(it, RegexSubset.namedGroups(it)) }
        assertEquals(setOf("amount", "payee"), RegexSubset.namedGroups(ok[0]))
    }

    @Test
    fun ruleCodeRoundTrips() {
        val rule = ParserRule(
            v = 1,
            id = "kotak-upi-sent",
            bank = "Kotak",
            senders = listOf("KOTAKB"),
            pattern = """Sent (?<amount>Rs\.[\d,.]+) from .+? to (?<payee>.+?) on""",
            directionWords = mapOf(
                RuleDirection.DEBIT to listOf("sent"),
                RuleDirection.REFUND to listOf("refund")
            ),
            accountType = RuleAccountType.BANK,
            dateFormat = null
        )
        val code = RuleCode.encode(rule)
        assertTrue(code.startsWith("khata1:"))
        assertTrue(code.none { it == '+' || it == '/' || it == '=' })
        assertEquals(RuleCodeResult.Decoded(rule), RuleCode.decode(code))
        // Chat apps wrap long text; whitespace anywhere is ignored.
        assertEquals(
            RuleCodeResult.Decoded(rule),
            RuleCode.decode(
                " " + code.chunked(20).joinToString("\n") + "\n"
            )
        )
    }

    @Test
    fun ruleCodeErrors() {
        fun code(json: String) = RuleCode.PREFIX + java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.toByteArray())
        assertEquals(RuleCodeResult.Error(RuleCodeError.BAD_PREFIX), RuleCode.decode("khata2:abc"))
        assertEquals(RuleCodeResult.Error(RuleCodeError.BAD_BASE64), RuleCode.decode("khata1:ab*c"))
        assertEquals(
            RuleCodeResult.Error(RuleCodeError.BAD_JSON),
            RuleCode.decode(code("{not json"))
        )
        assertEquals(RuleCodeResult.Error(RuleCodeError.BAD_JSON), RuleCode.decode(code("[1,2]")))
        assertEquals(
            RuleCodeResult.Error(RuleCodeError.UNKNOWN_VERSION),
            RuleCode.decode(code("""{"v":2,"future":true}"""))
        )
        assertEquals(
            RuleCodeResult.Error(RuleCodeError.UNKNOWN_VERSION),
            RuleCode.decode(code("""{"id":"x"}"""))
        )
        // Version 1 with a typo in a key is refused rather than half-read.
        val typo = """{"v":1,"id":"x","bank":"B","senders":["TESTBK"],""" +
            """"patern":"(?<amount>\\d+)","direction":"debit","accountType":"bank"}"""
        assertEquals(RuleCodeResult.Error(RuleCodeError.BAD_JSON), RuleCode.decode(code(typo)))
    }

    @Test
    fun nestedRepetitionStaysFast() {
        // Exponential on a backtracking engine; linear on RE2J, so even a long SMS is instant.
        val rule = ParserRule(
            v = 1,
            id = "nested",
            bank = "Test Bank",
            senders = listOf("TESTBK"),
            pattern = "Paid (?<amount>Rs\\.\\d+) (?:(a+)+)$",
            direction = RuleDirection.DEBIT,
            accountType = RuleAccountType.BANK
        )
        val parser = SmsParser(listOf((RuleValidator.validate(rule) as RuleCheck.Valid).rule))
        val start = System.nanoTime()
        val result = parser.parse("AX-TESTBK-S", "Paid Rs.40 " + "a".repeat(5_000) + "!", 0L)
        assertTrue("took too long", System.nanoTime() - start < TimeUnit.SECONDS.toNanos(1))
        assertEquals(ParseResult.Unparsed("Test Bank"), result)
    }
}
