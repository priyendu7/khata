package com.openhand.khata.sms.parser

import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the built-in rules over the real, redacted bank SMS in src/test/resources/samples/ (one
 * file per bank). PRD target: at least 95% parsed correctly, per bank.
 */
class SampleSmsTest {
    private val dir = File(requireNotNull(javaClass.getResource("/samples")).toURI())
    private val files = dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
    private val rules = BuiltInRules.load()

    private fun read(file: File): JsonObject =
        RuleCode.json.parseToJsonElement(file.readText()).jsonObject

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    @Test
    fun everyBuiltInBankHasSamples() {
        assertEquals(BuiltInRules.BANKS.map { "$it.json" }, files.map { it.name })
    }

    @Test
    fun accuracyPerBank() {
        val report = files.map { file ->
            val bank = read(file)
            val parser = SmsParser(rules, ZoneId.of(bank.string("zone")))
            val samples = bank.getValue("samples").jsonArray.map { it.jsonObject }
            val wrong = samples.filter { sample ->
                val result = parser.parse(
                    sample.string("sender"),
                    sample.string("body"),
                    millis(sample.string("receivedAt"))
                )
                expected(sample.getValue("expect")) != actual(result)
            }
            wrong.forEach { println("WRONG ${file.name}: ${it.string("body")}") }
            val correct = samples.size - wrong.size
            println("${bank.string("bank")}: $correct/${samples.size} parsed correctly")
            Triple(bank.string("bank"), correct, samples.size)
        }
        report.forEach { (bank, correct, total) ->
            assertTrue("$bank: at least 5 samples", total >= MIN_SAMPLES)
            assertTrue(
                "$bank: $correct/$total is below 95%",
                correct * PERCENT >= total * TARGET_PERCENT
            )
        }
    }

    /**
     * The samples are public, so no real personal numbers: fails on masked account numbers that
     * show more than 4 digits, card-length numbers, and long numbers that aren't a labelled
     * reference or a toll-free helpline.
     */
    /**
     * The samples are public, so no real personal numbers: fails on masked account numbers that
     * show more than 4 digits, card-length numbers, and long numbers that aren't a labelled
     * reference or a toll-free helpline.
     */
    @Test
    fun samplesAreRedacted() {
        files.forEach { file ->
            read(file).getValue("samples").jsonArray.forEach { sample ->
                val body = sample.jsonObject.string("body")
                val problems = redactionProblems(body)
                assertTrue("${file.name}: $problems in \"$body\"", problems.isEmpty())
            }
        }
    }

    @Test
    fun redactionCheckCatchesPersonalNumbers() {
        listOf(
            "Sent Rs.5 from A/c XXXXXXXXX509183 to SHOP",
            "Paid Rs.5 from A/c 50100123456789 to SHOP",
            "Call me on 9876543210",
            "Card 4111111111111111 used. UPI Ref 123456789012"
        ).forEach { assertTrue(it, redactionProblems(it).isNotEmpty()) }
        listOf(
            "Sent Rs.5 from A/c X1234 to SHOP. UPI Ref 123456789012",
            "Received Rs.1 on 10-09-26.UPI Ref:123456789012",
            "Call on 18602662666"
        ).forEach { assertTrue(it, redactionProblems(it).isEmpty()) }
    }

    private fun redactionProblems(body: String): List<String> {
        val unlabelled = LONG_NUMBER.findAll(body).filter { match ->
            val before = body.substring(0, match.range.first).takeLast(LABEL_WINDOW)
            !REF_LABEL.containsMatchIn(before) && TOLL_FREE.none(match.value::startsWith)
        }
        return MASKED_TOO_SHORT.findAll(body).map { it.value }.toList() +
            unlabelled.map { it.value }
    }

    private fun expected(expect: kotlinx.serialization.json.JsonElement): Map<String, Any?> =
        if (expect is JsonPrimitive) {
            mapOf("result" to expect.content)
        } else {
            expect.jsonObject.mapValues { (key, value) ->
                when {
                    value is JsonNull -> null
                    key == "timestamp" -> millis(value.jsonPrimitive.content)
                    key == "amountPaise" -> value.jsonPrimitive.content.toLong()
                    else -> value.jsonPrimitive.content
                }
            } + ("result" to "parsed")
        }

    private fun actual(result: ParseResult): Map<String, Any?> = when (result) {
        is ParseResult.Parsed -> with(result.sms) {
            mapOf(
                "result" to "parsed",
                "amountPaise" to amountPaise,
                "direction" to direction.name.lowercase(),
                "account" to accountLast4,
                "payee" to payee,
                "ref" to reference,
                "timestamp" to timestamp
            )
        }
        ParseResult.NotTransaction -> mapOf("result" to "not_transaction")
        is ParseResult.Unparsed -> mapOf("result" to "unparsed")
        ParseResult.UnknownSender -> mapOf("result" to "unknown_sender")
    }

    private fun millis(iso: String): Long = OffsetDateTime.parse(iso).toInstant().toEpochMilli()

    private companion object {
        const val MIN_SAMPLES = 5
        const val PERCENT = 100
        const val TARGET_PERCENT = 95
        const val LABEL_WINDOW = 12

        /** `XXXX509183`: a mask that still shows more than the last 4 digits. */
        val MASKED_TOO_SHORT = Regex("""[Xx*]{2,}\d{5,}""")

        /** Account numbers are 9–18 digits and cards 13–19, so anything 9+ digits long. */
        val LONG_NUMBER = Regex("""(?<!\d)\d{9,}(?!\d)""")

        /** UPI and bank references are fine: they're replaced with random digits. */
        val REF_LABEL =
            Regex("""ref(?:erence)?\s*(?:no\.?)?\s*[:.]?\s*$""", RegexOption.IGNORE_CASE)

        /** Banks' public toll-free numbers (1800…, 1860…). */
        val TOLL_FREE = listOf("1800", "1860")
    }
}
