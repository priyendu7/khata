package com.openhand.khata.sms.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Ignore messages like this" templates and ignore rules in the parser (PRD feature 7). */
class IgnoreTemplateTest {
    private val sms = "Recharge of Rs.299 for 9876543210 successful. Plan: Jio Unlimited, " +
        "valid till 30-10-2026. Txn ID 4467."

    private fun template(body: String, vararg tapped: String): IgnoreTemplate.Compiled {
        val words = RuleMaker.words(body).filter { it.text.trimEnd(',', '.') in tapped }
        val pattern = IgnoreTemplate.pattern(body, words)
        assertNotNull(pattern, RegexSubset.namedGroups(pattern))
        return requireNotNull(IgnoreTemplate.compile(pattern))
    }

    @Test
    fun matchesItsOwnSmsAndTheSameWithOtherNumbersAndTappedWords() {
        val like = template(sms, "Jio", "Unlimited")

        assertTrue(like.matches(sms))
        assertTrue(like.matches("  $sms\n"))
        assertTrue(
            like.matches(
                "Recharge of Rs.1,499 for 9123456780 successful. Plan: Annual Data Pack, " +
                    "valid till 01-01-2027. Txn ID 99812."
            )
        )
        // Case doesn't matter, as in parser rules.
        assertTrue(like.matches(sms.uppercase()))
    }

    @Test
    fun doesNotMatchADifferentMessageFromTheSameSender() {
        val like = template(sms, "Jio", "Unlimited")

        assertFalse(like.matches("Rs.299 debited from your Jio wallet for recharge. Txn ID 4467."))
        // An untapped word that differs is a different message.
        assertFalse(like.matches(sms.replace("successful", "failed")))
        assertFalse(like.matches("$sms Extra text."))
    }

    @Test
    fun specialCharactersAreLiteral() {
        val body = "Pay (now) at https://x.co/a?b=1 + get 2* points [T&C]"
        val like = template(body)

        assertTrue(like.matches(body))
        assertFalse(like.matches("Pay now at https://x.co/a?b=1 + get 2* points [T&C]"))
    }

    @Test
    fun anIgnoredSenderIsDroppedBeforeItsTextIsRead() {
        val rules = IgnoreRules(listOf(IgnoreRule(7, "HDFCBK", null)))
        val parser = SmsParser(BuiltInRules.load(), ignore = rules)

        assertFalse(parser.accepts("VM-HDFCBK-S"))
        assertTrue(parser.accepts("JM-KOTAKB-S"))
        val explained = parser.explain("VM-HDFCBK-S", "Rs.450 spent on card", 0)
        assertEquals(ParseResult.Filtered(FilterReason.IgnoredSender(7)), explained.result)
        assertEquals(emptyList<String>(), explained.rulesTried)
    }

    @Test
    fun likeThisDropsOnlyWhatNoRuleReadsFromThatSender() {
        val kotak = "Sent Rs.366.00 from Kotak Bank A/c X1234 to GENERAL STORE on 23-09-26. " +
            "UPI Ref 111122223333. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud"
        val unread = "Rs.2,000 withdrawn at ATM using card XX5678."
        val rules = IgnoreRules(
            listOf(
                IgnoreRule(1, "KOTAKB", IgnoreTemplate.pattern(kotak, emptyList())),
                IgnoreRule(2, "KOTAKB", IgnoreTemplate.pattern(unread, emptyList()))
            )
        )
        val parser = SmsParser(BuiltInRules.load(), ignore = rules)

        // A rule that reads an SMS always wins.
        assertTrue(parser.parse("JM-KOTAKB-S", kotak, 0) is ParseResult.Parsed)
        assertEquals(
            ParseResult.Filtered(FilterReason.IgnoredLikeThis(2)),
            parser.parse("JM-KOTAKB-S", "Rs.500 withdrawn at ATM using card XX9999.", 0)
        )
        // Only for the sender it was made for.
        assertTrue(parser.parse("VM-HDFCBK-S", unread, 0) is ParseResult.Unparsed)
    }

    @Test
    fun aTemplateThatCannotRunIsSkipped() {
        assertEquals(null, IgnoreTemplate.compile("(?<amount>\\d+)"))
        assertEquals(null, IgnoreTemplate.compile("(unclosed"))
        val rules = IgnoreRules(listOf(IgnoreRule(1, "KOTAKB", "(unclosed")))
        assertEquals(null, rules.likeThisRule("KOTAKB", "(unclosed"))
    }
}
