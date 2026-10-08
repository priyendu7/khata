package com.openhand.khata.sms.parser

import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SmsParser.readWithoutSender], which the transfer back-fill (#113) reads saved SMS with. */
class ReadWithoutSenderTest {
    private val parser = SmsParser(BuiltInRules.load(), ZoneId.of("Asia/Kolkata"))

    @Test
    fun kotakSamplesReadTheSameWithoutTheirSender() {
        val file = File(requireNotNull(javaClass.getResource("/samples/kotak.json")).toURI())
        val samples = RuleCode.json.parseToJsonElement(file.readText()).jsonObject
            .getValue("samples").jsonArray.map { it.jsonObject }
        var read = 0
        samples.forEach { sample ->
            val sender = sample.getValue("sender").jsonPrimitive.content
            val body = sample.getValue("body").jsonPrimitive.content
            val receivedAt = OffsetDateTime.parse(
                sample.getValue("receivedAt").jsonPrimitive.content
            ).toInstant().toEpochMilli()
            val parsed = parser.parse(sender, body, receivedAt) as? ParseResult.Parsed
                ?: return@forEach

            // The account's bank as a user may have typed it.
            val again = parser.readWithoutSender(body, receivedAt, "Kotak Mahindra Bank")

            assertEquals(body, parsed.sms, again)
            read++
        }
        assertTrue(read > 0)
    }

    @Test
    fun anSmsNoRuleReadsIsNull() {
        assertNull(parser.readWithoutSender("Your OTP is 123456", 1_790_000_000_000L, null))
    }
}
