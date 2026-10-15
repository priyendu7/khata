package com.openhand.khata.sms.parser

import com.openhand.khata.sms.parser.RuleMaker.Field
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs [FieldGuesser] over every transaction in the repo's bank samples and shared rule vectors.
 * Target: the amount right for at least 98% of them, and every guessed field for at least 90%.
 * Vectors only check the fields their rule reads; a rule that skips the payee says nothing about
 * it.
 */
class FieldGuesserAccuracyTest {
    private data class Sample(
        val sender: String,
        val body: String,
        val receivedAt: Long,
        val zone: ZoneId,
        val expect: JsonObject
    )

    private fun read(dir: String): List<Sample> {
        val root = File(requireNotNull(javaClass.getResource("/$dir")).toURI())
        return root.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
            .flatMap { file ->
                val json = RuleCode.json.parseToJsonElement(file.readText()).jsonObject
                val zone = ZoneId.of(json["zone"]?.jsonPrimitive?.content ?: "Asia/Kolkata")
                val list = json["samples"] ?: json["cases"] ?: return@flatMap emptyList()
                list.jsonArray.map { it.jsonObject }.mapNotNull { case ->
                    val expect = case.getValue("expect") as? JsonObject ?: return@mapNotNull null
                    if (expect["result"]?.jsonPrimitive?.content.let {
                            it != null && it != "parsed"
                        }
                    ) {
                        return@mapNotNull null
                    }
                    Sample(
                        case.getValue("sender").jsonPrimitive.content,
                        case.getValue("body").jsonPrimitive.content,
                        millis(case.getValue("receivedAt").jsonPrimitive.content),
                        zone,
                        expect
                    )
                }
            }
    }

    private val samples = (read("samples") + read("rule-vectors")).distinctBy { it.body }

    /** The fields of [sample] the guess got wrong, by name. */
    private fun wrong(sample: Sample): List<String> {
        val guesses = FieldGuesser.guess(sample.body)
        val actual = mapOf(
            "amountPaise" to guesses.amountPaise?.toString(),
            "direction" to guesses.direction?.name?.lowercase(),
            "account" to guesses[Field.ACCOUNT]?.text?.let(Fields::last4),
            "payee" to guesses[Field.PAYEE]?.text?.let(Fields::clean),
            "ref" to guesses[Field.REF]?.text,
            "date" to guessedDate(guesses, sample)
        )
        val expected = sample.expect.filterKeys { it in actual || it == "timestamp" }
        return expected.mapNotNull { (key, value) ->
            val want = if (value is JsonNull) null else value.jsonPrimitive.content
            when {
                // Vectors leave out what their rule doesn't read.
                want == null && sample.expect["result"] != null -> null
                key == "timestamp" -> key.takeIf {
                    actual["date"] != null &&
                        actual["date"] != OffsetDateTime.parse(want).atZoneSameInstant(sample.zone)
                            .toLocalDate().toString()
                }
                actual[key] != want -> key
                else -> null
            }
        }
    }

    private fun guessedDate(guesses: FieldGuesser.Guesses, sample: Sample): String? {
        val date = guesses[Field.DATE] ?: return null
        // A date no format reads counts as wrong: the body never equals a date.
        return runCatching {
            val formatter = DateTimeFormatterBuilder().parseCaseInsensitive()
                .appendPattern(guesses.dateFormat).toFormatter(Locale.ENGLISH)
            LocalDate.from(formatter.parse(date.text)).toString()
        }.getOrDefault(sample.body)
    }

    @Test
    fun accuracy() {
        val results = samples.map { it to wrong(it) }
        results.filter { it.second.isNotEmpty() }
            .forEach { (sample, fields) -> println("GUESS WRONG $fields: ${sample.body}") }
        val total = results.size
        val allRight = results.count { it.second.isEmpty() }
        val amountRight = results.count { "amountPaise" !in it.second }
        println("Guesser: all fields $allRight/$total, amount $amountRight/$total")
        assertTrue("only $total samples", total >= MIN_SAMPLES)
        assertTrue("amount $amountRight/$total", amountRight * PERCENT >= total * AMOUNT_TARGET)
        assertTrue("all fields $allRight/$total", allRight * PERCENT >= total * FIELDS_TARGET)
    }

    /** A rule made from the guesses reads the SMS it was made from, for every Kotak sample. */
    @Test
    fun ruleFromGuessReadsItsSms() {
        read("samples").forEach { sample ->
            val draft = FieldGuesser.guess(sample.body).draft("guess", "Bank", listOf("KOTAKB"))
            val check = RuleValidator.validate(RuleMaker.rule(requireNotNull(draft)))
            val rule = (check as? RuleCheck.Valid)?.rule
            val read = rule?.let {
                SmsParser(
                    listOf(it),
                    sample.zone
                ).parse(sample.sender, sample.body, sample.receivedAt)
            }
            assertTrue("$check / $read: ${sample.body}", read is ParseResult.Parsed)
        }
    }

    private fun millis(iso: String): Long = OffsetDateTime.parse(iso).toInstant().toEpochMilli()

    private companion object {
        const val MIN_SAMPLES = 20
        const val PERCENT = 100
        const val AMOUNT_TARGET = 98
        const val FIELDS_TARGET = 90
    }
}
