package com.openhand.khata.sms.parser

import java.util.Base64
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomRulesTest {
    private val atm = ParserRule(
        v = 1,
        id = "kotak-atm",
        bank = "Kotak",
        senders = listOf("KOTAKB"),
        pattern = "(?<amount>Rs\\.[\\d,]+) withdrawn at ATM using card XX(?<account>\\d{4})",
        direction = RuleDirection.DEBIT,
        accountType = RuleAccountType.DEBIT_CARD
    )
    private val atmSms = "Rs.2,000 withdrawn at ATM using card XX5678."
    private val upiSms = "Sent Rs.366.00 from Kotak Bank A/c X1234 to GENERAL STORE on 23-09-26. " +
        "UPI Ref 111122223333. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud"

    private fun codeOf(json: String) =
        RuleCode.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

    @Test
    fun aGoodCodeIsReadAndChecked() {
        val check = CustomRules.check(RuleCode.encode(atm))

        assertEquals(atm, (check as CodeCheck.Valid).rule.rule)
    }

    @Test
    fun lineBreaksFromChatAppsDontMatter() {
        val code = RuleCode.encode(atm).chunked(20).joinToString("\n ")

        assertTrue(CustomRules.check(code) is CodeCheck.Valid)
    }

    @Test
    fun aCodeWithoutThePrefixIsRejected() {
        val code = RuleCode.encode(atm).removePrefix(RuleCode.PREFIX)

        assertEquals(CodeCheck.Unreadable(RuleCodeError.BAD_PREFIX), CustomRules.check(code))
        assertEquals(
            CodeCheck.Unreadable(RuleCodeError.BAD_PREFIX),
            CustomRules.check("khata2:" + code)
        )
    }

    @Test
    fun brokenBase64AndJsonAreRejected() {
        assertEquals(
            CodeCheck.Unreadable(RuleCodeError.BAD_BASE64),
            CustomRules.check("khata1:not*base64")
        )
        assertEquals(
            CodeCheck.Unreadable(RuleCodeError.BAD_JSON),
            CustomRules.check(codeOf("""{"v":1,"id":"x","patern":"typo"}"""))
        )
    }

    @Test
    fun aRuleForANewerVersionIsRejected() {
        val json = RuleCode.json.encodeToString(ParserRule.serializer(), atm.copy(v = 2))

        assertEquals(
            CodeCheck.Unreadable(RuleCodeError.UNKNOWN_VERSION),
            CustomRules.check(codeOf(json))
        )
    }

    @Test
    fun aRuleWithoutAnAmountIsRejected() {
        val rule = atm.copy(pattern = "withdrawn at ATM using card XX(?<account>\\d{4})")

        assertEquals(
            CodeCheck.Invalid(listOf(RuleError.MISSING_AMOUNT)),
            CustomRules.check(RuleCode.encode(rule))
        )
    }

    @Test
    fun patternsThatCouldBeSlowAreRejected() {
        // Backreferences are the one feature that would make matching slow; RE2 refuses them.
        val backreference = atm.copy(pattern = "(?<amount>Rs\\.\\d+)(a+)\\2+b")
        val tooLong = atm.copy(pattern = "(?<amount>Rs\\.\\d+)" + "a?".repeat(600))

        assertEquals(
            CodeCheck.Invalid(listOf(RuleError.UNSUPPORTED_PATTERN)),
            CustomRules.check(RuleCode.encode(backreference))
        )
        assertEquals(
            CodeCheck.Invalid(listOf(RuleError.PATTERN_TOO_LONG)),
            CustomRules.check(RuleCode.encode(tooLong))
        )
    }

    @Test
    fun nestedRepeatsThatFreezeOtherEnginesStayFast() {
        // (a+)+$ takes exponential time on a backtracking engine; on RE2 it's linear.
        val nested = atm.copy(pattern = "(?<amount>Rs\\.\\d+) (?:a+)+$")
        val rule = (CustomRules.check(RuleCode.encode(nested)) as CodeCheck.Valid).rule
        val body = "Rs.5 " + "a".repeat(5_000) + "!"

        val started = System.nanoTime()
        val result = SmsParser(listOf(rule)).parse("JM-KOTAKB-S", body, AT)

        assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started) < 2)
        assertTrue(result !is ParseResult.Parsed)
    }

    @Test
    fun aCustomRuleWinsOverABuiltInOneForTheSameSms() {
        val custom = atm.copy(
            id = "my-kotak-upi",
            bank = "Kotak (mine)",
            pattern = "Sent (?<amount>Rs\\.[\\d.,]+) from Kotak Bank A/c X(?<account>\\d{4}) " +
                "to (?<payee>.+?) on"
        )
        val parser = CustomRules.parser(
            CustomRules.load(listOf(RuleCode.encode(custom))),
            BuiltInRules.load()
        )

        val parsed = (parser.parse("JM-KOTAKB-S", upiSms, AT) as ParseResult.Parsed).sms

        assertEquals("my-kotak-upi", parsed.ruleId)
        assertEquals("Kotak (mine)", parsed.bank)
    }

    @Test
    fun customRulesAddSendersAndFixFormatsTheBuiltInsMiss() {
        val builtInOnly = SmsParser(BuiltInRules.load())
        assertEquals(ParseResult.Unparsed("Kotak"), builtInOnly.parse("JM-KOTAKB-S", atmSms, AT))

        val other = atm.copy(id = "other-atm", bank = "Other", senders = listOf("OTHRBK"))
        val parser = CustomRules.parser(
            CustomRules.load(listOf(RuleCode.encode(atm), RuleCode.encode(other))),
            BuiltInRules.load()
        )

        assertTrue(parser.isKnownSender("AX-OTHRBK-S"))
        val parsed = (parser.parse("JM-KOTAKB-S", atmSms, AT) as ParseResult.Parsed).sms
        assertEquals(200_000L, parsed.amountPaise)
        assertEquals("5678", parsed.accountLast4)
    }

    @Test
    fun storedCodesThatNoLongerPassAreSkipped() {
        val broken = codeOf("""{"v":1}""")

        val rules = CustomRules.load(listOf(broken, RuleCode.encode(atm), "rubbish"))

        assertEquals(listOf("kotak-atm"), rules.map { it.rule.id })
    }

    private companion object {
        const val AT = 1_790_000_000_000L
    }
}
