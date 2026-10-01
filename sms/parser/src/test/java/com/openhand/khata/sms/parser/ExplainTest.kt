package com.openhand.khata.sms.parser

import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SmsParser.explain], which Settings > SMS import > Test a message shows (PRD feature 7). */
class ExplainTest {
    private val rules = BuiltInRules.load()
    private val parser = SmsParser(rules, ZoneId.of("Asia/Kolkata"))
    private val at = 1_790_000_000_000L

    @Test
    fun kotakSamplesExplainTheSameAsTheyParse() {
        val file = File(requireNotNull(javaClass.getResource("/samples/kotak.json")).toURI())
        val samples = RuleCode.json.parseToJsonElement(file.readText()).jsonObject
            .getValue("samples").jsonArray.map { it.jsonObject }
        val kotakIds = rules.filter { "KOTAKB" in it.headers }.map { it.rule.id }
        samples.forEach { sample ->
            val sender = sample.getValue("sender").jsonPrimitive.content
            val body = sample.getValue("body").jsonPrimitive.content
            val receivedAt = OffsetDateTime.parse(
                sample.getValue("receivedAt").jsonPrimitive.content
            )
                .toInstant().toEpochMilli()

            val explanation = parser.explain(sender, body, receivedAt)

            assertEquals(body, parser.parse(sender, body, receivedAt), explanation.result)
            assertTrue(body, explanation.rulesTried.isNotEmpty())
            assertTrue(body, kotakIds.containsAll(explanation.rulesTried))
            val parsed = explanation.result as? ParseResult.Parsed
            if (parsed != null) {
                assertEquals(body, parsed.sms.ruleId, explanation.rulesTried.last())
            } else {
                // Nothing read it, so every Kotak rule was tried.
                assertEquals(body, kotakIds, explanation.rulesTried)
            }
        }
    }

    @Test
    fun aSenderFilterTriesNoRule() {
        val explanation = parser.explain("+919876543210", "Sent Rs.5 from Kotak Bank", at)

        assertEquals(ParseResult.Filtered(FilterReason.PhoneNumber), explanation.result)
        assertEquals(emptyList<String>(), explanation.rulesTried)
    }

    @Test
    fun everyFilterReasonHasAnExample() {
        val sent = "Rs.500 debited from your account"
        val examples = listOf(
            Triple("9876543210", sent, FilterReason.PhoneNumber),
            Triple("AX-SHOPXY-P", sent, FilterReason.Promotional),
            Triple("AX-SHOPXY-G", sent, FilterReason.Government),
            Triple("AX-SHOPXY-T", sent, FilterReason.NotService),
            Triple("AX-SHOPXY-S", "Your order has been debited soon", FilterReason.NoAmount),
            Triple("AX-SHOPXY-S", "Your bill of Rs.500 is ready", FilterReason.NoTransactionWord)
        ) + mapOf(
            NotTransactionGroup.OTP to "123456 is your OTP for Rs.500 debit",
            NotTransactionGroup.COLLECT_REQUEST to "SHOP has requested money Rs.500 via UPI",
            NotTransactionGroup.REMINDER to "Your card bill of Rs.500 is due on 05-10",
            NotTransactionGroup.BALANCE to "Avl bal in A/c X1234 is Rs.500",
            NotTransactionGroup.MANDATE to "E-mandate of Rs.500 set up for SHOP",
            NotTransactionGroup.OFFER to "Pre-approved loan of Rs.50000 credited instantly",
            NotTransactionGroup.FAILED to "Payment of Rs.500 to SHOP failed"
        ).map { (group, body) ->
            Triple("AX-SHOPXY-S", body, group)
        }
        examples.forEach { (sender, body, expected) ->
            val result = parser.explain(sender, body, at).result
            val reason = (result as ParseResult.Filtered).reason
            if (expected is NotTransactionGroup) {
                assertEquals(body, expected, (reason as FilterReason.NotTransaction).group)
            } else {
                assertEquals(body, expected, reason)
            }
        }
        val groups = examples.map { it.third }.filterIsInstance<NotTransactionGroup>()
        assertEquals(NotTransactionGroup.entries.toSet(), groups.toSet())
    }
}
