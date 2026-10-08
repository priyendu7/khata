package com.openhand.khata.sms.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The user's changes to built-in rules (Settings > Parsers), applied on top of them. */
class BuiltInOverridesTest {
    private val builtIn = BuiltInRules.load()
    private val sent = BuiltInRules.all().first { it.id == "kotak-upi-sent" }
    private val upiSms = "Sent Rs.366.00 from Kotak Bank A/c X1234 to GENERAL STORE on 23-09-26. " +
        "UPI Ref 111122223333. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud"
    private val at = 1_790_000_000_000L

    private fun ids(rules: List<CompiledRule>) = rules.map { it.rule.id }

    private fun parse(rules: List<CompiledRule>) = SmsParser(rules).parse("JM-KOTAKB-S", upiSms, at)

    @Test
    fun noOverridesKeepsEveryRuleInOrder() {
        assertEquals(ids(builtIn), ids(BuiltInRules.withOverrides(builtIn, emptyList())))
    }

    @Test
    fun aRuleThatIsOffIsNotUsed() {
        val rules = BuiltInRules.withOverrides(
            builtIn,
            listOf(RuleOverride("kotak-upi-sent", enabled = false, editedCode = null))
        )

        assertEquals(ids(builtIn) - "kotak-upi-sent", ids(rules))
        assertTrue(parse(rules) is ParseResult.Unparsed)
    }

    @Test
    fun anEditedRuleRunsInPlaceOfTheOriginalInTheSamePosition() {
        val edited = sent.copy(bank = "My Kotak")
        val rules = BuiltInRules.withOverrides(
            builtIn,
            listOf(RuleOverride(sent.id, enabled = true, editedCode = RuleCode.encode(edited)))
        )

        assertEquals(ids(builtIn), ids(rules))
        assertEquals(edited, rules[ids(builtIn).indexOf(sent.id)].rule)
        assertEquals("My Kotak", (parse(rules) as ParseResult.Parsed).sms.bank)
    }

    @Test
    fun anEditThatFailsTheChecksFallsBackToTheOriginal() {
        val broken = RuleCode.encode(sent.copy(pattern = "Sent (?<amount>[\\d.]+"))
        val otherId = RuleCode.encode(sent.copy(id = "kotak-other", bank = "Other"))

        listOf(broken, otherId, "khata1:damaged").forEach { code ->
            val rules = BuiltInRules.withOverrides(
                builtIn,
                listOf(RuleOverride(sent.id, enabled = true, editedCode = code))
            )
            assertEquals(builtIn.map { it.rule }, rules.map { it.rule })
        }
    }

    @Test
    fun anOverrideForARemovedRuleIsIgnored() {
        val rules = BuiltInRules.withOverrides(
            builtIn,
            listOf(
                RuleOverride("kotak-gone", enabled = false, editedCode = null),
                RuleOverride("hdfc-gone", enabled = true, editedCode = RuleCode.encode(sent))
            )
        )

        assertEquals(ids(builtIn), ids(rules))
    }

    @Test
    fun theHashIsStableAndChangesWithTheRule() {
        assertEquals(BuiltInRules.hash(sent), BuiltInRules.hash(sent.copy()))
        assertNotEquals(
            BuiltInRules.hash(sent),
            BuiltInRules.hash(sent.copy(dateFormat = "d-M-yy"))
        )
        assertEquals(64, BuiltInRules.hash(sent).length)
    }
}
