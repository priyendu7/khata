package com.openhand.khata.sms.parser

import com.openhand.khata.core.model.Direction
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Statement rules and the generic built-in card rules (#153). */
class StatementRulesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val builtIn = BuiltInRules.load()
    private val parser = SmsParser(builtIn, zone)
    private val at = 1_790_000_000_000L

    private val icici = "ICICI Bank Credit Card XX1234 Statement is sent to a*****1@example.com. " +
        "Total of Rs 35,299.72 or minimum of Rs 3,450.00 is due by 23-OCT-26."
    private val axis = "Your statement for Axis Bank Credit Card no. XX5678 is generated.\n" +
        "Due on: 02-11-26\nTotal amt: INR  Dr. 8695.00\nMin amt due: INR  Dr. 174.00\n" +
        "Pay at axis.bank.in/ccpaynow\nTo view/download statement: https://example.com/abc"
    private val hdfc = "HDFC Bank Credit Card XX4321 Statement:\nTotal due: Rs.8,363.00\n" +
        "Min.due: Rs.2,268.00\nPay by 02-10-2025\nView: https://example.com/xyz"
    private val hdfcPayment = "DEAR HDFCBANK CARDMEMBER, PAYMENT OF Rs. 9060.00 RECEIVED " +
        "TOWARDS YOUR CREDIT CARD ENDING WITH 4321 ON 14-10-2026.YOUR AVAILABLE LIMIT IS " +
        "RS. 49332.05"

    private fun statement(sender: String, body: String): ParsedStatement? =
        (parser.parse(sender, body, at) as? ParseResult.Statement)?.statement

    @Test
    fun genericRulesReadEachStatementFormat() {
        val read = statement("JM-ICICIT-S", icici)
        assertEquals("ICICI", read?.bank)
        assertEquals("1234", read?.cardLast4)
        assertEquals(3_529_972L, read?.totalPaise)
        assertEquals(345_000L, read?.minDuePaise)
        assertEquals(LocalDate.of(2026, 10, 23), read?.dueDate)
        assertEquals(869_500L, statement("AX-AXISBK-S", axis)?.totalPaise)
        assertEquals(LocalDate.of(2025, 10, 2), statement("VM-HDFCBK-S", hdfc)?.dueDate)
    }

    @Test
    fun aStatementIsntDroppedAsABillReminder() {
        val withoutGeneric = SmsParser(builtIn.filterNot { it.rule.anySender }, zone)
        val dropped = withoutGeneric.parse("VM-HDFCBK-S", hdfc, at) as ParseResult.Filtered
        assertEquals(
            NotTransactionGroup.REMINDER,
            (dropped.reason as FilterReason.NotTransaction).group
        )

        assertTrue(parser.parse("VM-HDFCBK-S", hdfc, at) is ParseResult.Statement)
    }

    @Test
    fun theAvailableLimitIsNeverTheAmount() {
        val read = (parser.parse("VM-HDFCBK-S", hdfcPayment, at) as ParseResult.Parsed).sms
        assertEquals(906_000L, read.amountPaise)
        assertEquals(Direction.CREDIT, read.direction)
        assertEquals("HDFC", read.bank)
        assertEquals("4321", read.accountLast4)
    }

    @Test
    fun aBanksOwnRuleWinsOverTheGenericOnes() {
        val own = RuleMaker.rule(
            RuleMaker.Draft(
                id = "hdfc-payment",
                bank = "HDFC Bank",
                senders = listOf("HDFCBK"),
                body = hdfcPayment,
                marks = listOf(
                    hdfcPayment.indexOf("9060.00").let {
                        RuleMaker.Mark(RuleMaker.Field.AMOUNT, it, it + "9060.00".length)
                    }
                ),
                direction = RuleDirection.CREDIT,
                accountType = RuleAccountType.CREDIT_CARD
            )
        )
        val custom = (RuleValidator.validate(own) as RuleCheck.Valid).rule
        // Custom rules come first, but generic ones would come after built-in ones anyway.
        val both = CustomRules.parser(listOf(custom), builtIn)
        val read = (both.parse("VM-HDFCBK-S", hdfcPayment, at) as ParseResult.Parsed).sms
        assertEquals("hdfc-payment", read.ruleId)
        val genericFirst = SmsParser(builtIn + custom, zone)
        val stillOwn = genericFirst.parse("VM-HDFCBK-S", hdfcPayment, at) as ParseResult.Parsed
        assertEquals("hdfc-payment", stillOwn.sms.ruleId)
    }

    @Test
    fun readingWithoutASenderSkipsStatementRules() {
        assertNull(parser.readWithoutSender(icici, at, "ICICI"))
        assertEquals(906_000L, parser.readWithoutSender(hdfcPayment, at, null)?.amountPaise)
    }

    @Test
    fun ruleCodesCarryV2Rules() {
        val rule = BuiltInRules.all().first { it.isStatement }
        val code = RuleCode.encode(rule)
        assertTrue(code.startsWith(RuleCode.PREFIX))
        assertEquals(rule, (RuleCode.decode(code) as RuleCodeResult.Decoded).rule)
        assertTrue(CustomRules.check(code) is CodeCheck.Valid)
    }

    @Test
    fun aV1RuleIsWrittenAsBefore() {
        val kotak = BuiltInRules.rules("kotak").first()
        val json = RuleCode.json.encodeToString(ParserRule.serializer(), kotak)
        assertTrue(json, json.startsWith("""{"v":1,"id":"kotak-upi-sent","bank":"Kotak","""))
        assertFalse(json, "kind" in json || "dueDateFormat" in json)
        assertEquals(RuleFormat.V1, RuleFormat.versionFor(kotak))
    }

    @Test
    fun theMakerMakesAStatementRuleFromMarks() {
        fun mark(field: RuleMaker.Field, text: String): RuleMaker.Mark {
            val start = hdfc.indexOf(text)
            return RuleMaker.Mark(field, start, start + text.length)
        }
        val draft = RuleMaker.Draft(
            id = "hdfc-statement",
            bank = "HDFC",
            senders = listOf("HDFCBK"),
            body = hdfc,
            marks = listOf(
                mark(RuleMaker.Field.ACCOUNT, "XX4321"),
                mark(RuleMaker.Field.TOTAL, "Rs.8,363.00"),
                mark(RuleMaker.Field.MIN_DUE, "Rs.2,268.00"),
                mark(RuleMaker.Field.DUE_DATE, "02-10-2025"),
                // Not a statement field, so left out of the pattern.
                mark(RuleMaker.Field.PAYEE, "View")
            ),
            direction = RuleDirection.DEBIT,
            accountType = RuleAccountType.BANK,
            dateFormat = "dd-MM-yyyy",
            kind = RuleKind.STATEMENT
        )
        val rule = RuleMaker.rule(draft)
        assertEquals(RuleFormat.VERSION, rule.v)
        assertEquals(RuleKind.STATEMENT, rule.kind)
        assertNull(rule.direction)
        assertNull(rule.accountType)
        assertEquals("dd-MM-yyyy", rule.dueDateFormat)
        assertFalse(rule.pattern, RuleFormat.PAYEE in rule.pattern)
        val compiled = (RuleValidator.validate(rule) as RuleCheck.Valid).rule
        val read = SmsParser(listOf(compiled), zone).parse("VM-HDFCBK-S", hdfc, at)
        assertEquals(226_800L, (read as ParseResult.Statement).statement.minDuePaise)
    }

    @Test
    fun theStatementRuleIdSaysStatement() {
        assertEquals(
            "hdfc-bank-statement-2",
            RuleMaker.ruleId("HDFC Bank", null, setOf("hdfc-bank-statement"))
        )
    }

    @Test
    fun theGuesserSpotsStatementFields() {
        listOf(
            Triple(icici, "Rs 35,299.72", "Rs 3,450.00"),
            Triple(axis, "INR  Dr. 8695.00", "INR  Dr. 174.00"),
            Triple(hdfc, "Rs.8,363.00", "Rs.2,268.00")
        ).forEach { (body, total, minimum) ->
            val guesses = FieldGuesser.guess(body)
            assertEquals(body, RuleKind.STATEMENT, guesses.kind)
            assertEquals(body, total, guesses[RuleMaker.Field.TOTAL]?.text)
            assertEquals(body, minimum, guesses[RuleMaker.Field.MIN_DUE]?.text)
            assertTrue(body, guesses[RuleMaker.Field.DUE_DATE] != null)
            assertTrue(body, guesses[RuleMaker.Field.ACCOUNT] != null)
            val draft = requireNotNull(guesses.draft("guess", "Bank", listOf("TESTBK")))
            val rule = (RuleValidator.validate(RuleMaker.rule(draft)) as RuleCheck.Valid).rule
            val read = SmsParser(listOf(rule), zone).parse("AX-TESTBK-S", body, at)
            assertTrue(body, read is ParseResult.Statement)
        }
    }

    @Test
    fun paymentsAndOffersDontLookLikeStatements() {
        assertFalse(StatementGuesser.looksLikeStatement(hdfcPayment))
        assertFalse(
            StatementGuesser.looksLikeStatement(
                "Get 5X rewards on your ICICI Bank Credit Card XX1234! See your statement."
            )
        )
        assertEquals(RuleKind.TRANSACTION, FieldGuesser.guess(hdfcPayment).kind)
    }
}
