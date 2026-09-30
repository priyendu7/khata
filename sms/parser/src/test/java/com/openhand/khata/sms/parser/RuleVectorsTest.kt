package com.openhand.khata.sms.parser

import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Runs the shared vectors in src/test/resources/rule-vectors/. The parser website (#60) runs the
 * same files, so the app and the website agree on every rule. Format: see README.md there.
 */
class RuleVectorsTest {
    private val dir = File(requireNotNull(javaClass.getResource("/rule-vectors")).toURI())
    private val files = dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }

    private fun read(file: File): JsonObject =
        RuleCode.json.parseToJsonElement(file.readText()).jsonObject

    @Test
    fun vectorsExist() {
        assertTrue(files.any { it.name == "invalid-rules.json" })
        assertTrue(files.count { it.name != "invalid-rules.json" } >= 4)
    }

    @Test
    fun parseCases() {
        files.filter { it.name != "invalid-rules.json" }.forEach { file ->
            val vector = read(file)
            val rules = vector.getValue("rules").jsonArray.map { element ->
                val rule = (RuleCode.fromJson(element.jsonObject) as RuleCodeResult.Decoded).rule
                when (val check = RuleValidator.validate(rule)) {
                    is RuleCheck.Valid -> check.rule
                    is RuleCheck.Invalid -> fail(
                        "${file.name}: rule ${rule.id} is invalid: ${check.errors}"
                    )
                } as CompiledRule
            }
            val parser = SmsParser(rules, ZoneId.of(vector.string("zone")))
            vector.getValue("cases").jsonArray.forEach { case ->
                val c = case.jsonObject
                val label = "${file.name}: ${c.string("name")}"
                val result = parser.parse(
                    c.string("sender"),
                    c.string("body"),
                    millis(c.string("receivedAt"))
                )
                assertEquals(label, expected(c.getValue("expect").jsonObject), actual(result))
            }
        }
    }

    @Test
    fun invalidRules() {
        read(File(dir, "invalid-rules.json")).getValue("rules").jsonArray.forEach { case ->
            val c = case.jsonObject
            val rule = (
                RuleCode.fromJson(
                    c.getValue("rule").jsonObject
                ) as? RuleCodeResult.Decoded
                )?.rule
            // A newer version is refused while decoding, before the validator sees it.
            val errors = if (rule == null) {
                listOf(RuleError.UNKNOWN_VERSION.code)
            } else {
                when (val check = RuleValidator.validate(rule)) {
                    is RuleCheck.Valid -> emptyList()
                    is RuleCheck.Invalid -> check.errors.map { it.code }
                }
            }
            val expected = c.getValue("errors").jsonArray.map { it.jsonPrimitive.content }
            assertEquals(c.string("name"), expected, errors)
        }
    }

    /** The expected result as a flat map, with timestamps as instants so offsets don't matter. */
    private fun expected(expect: JsonObject): Map<String, Any?> = expect.mapValues { (key, value) ->
        when {
            value is JsonNull -> null
            key == "timestamp" -> millis(value.jsonPrimitive.content)
            key == "amountPaise" -> value.jsonPrimitive.content.toLong()
            else -> (value as JsonPrimitive).contentOrNull
        }
    }

    private fun actual(result: ParseResult): Map<String, Any?> = when (result) {
        is ParseResult.Parsed -> with(result.sms) {
            mapOf(
                "result" to "parsed",
                "ruleId" to ruleId,
                "bank" to bank,
                "amountPaise" to amountPaise,
                "direction" to direction.name.lowercase(),
                "accountType" to
                    RuleAccountType.entries.first { it.accountType == accountType }.serialName(),
                "account" to accountLast4,
                "payee" to payee,
                "ref" to reference,
                "timestamp" to timestamp
            )
        }
        ParseResult.NotTransaction -> mapOf("result" to "not_transaction")
        is ParseResult.Unparsed -> mapOf("result" to "unparsed", "bank" to result.bank)
        ParseResult.UnknownSender -> mapOf("result" to "unknown_sender")
    }

    private fun RuleAccountType.serialName(): String =
        RuleCode.json.encodeToJsonElement(RuleAccountType.serializer(), this).jsonPrimitive.content

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun millis(iso: String): Long = OffsetDateTime.parse(iso).toInstant().toEpochMilli()
}
