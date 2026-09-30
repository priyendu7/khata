package com.openhand.khata.sms.parser

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The shareable form of a rule: `khata1:` followed by the rule's JSON in unpadded base64url, so it
 * survives being pasted through chat apps. The rule maker shows these to share; Settings > Parsers
 * reads them.
 */
object RuleCode {
    const val PREFIX = "khata1:"

    /** Strict: an unknown key is an error, so a typo in a hand-written rule isn't ignored. */
    internal val json = Json {
        explicitNulls = false
        encodeDefaults = false
    }

    fun encode(rule: ParserRule): String {
        val bytes = json.encodeToString(ParserRule.serializer(), rule).toByteArray(Charsets.UTF_8)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** Decodes a pasted code. Whitespace anywhere (line breaks from chat apps) is ignored. */
    fun decode(code: String): RuleCodeResult {
        val compact = code.filterNot(Char::isWhitespace)
        val hasPrefix = compact.startsWith(PREFIX)
        val bytes = if (hasPrefix) base64(compact.removePrefix(PREFIX)) else null
        return when {
            !hasPrefix -> RuleCodeResult.Error(RuleCodeError.BAD_PREFIX)
            bytes == null -> RuleCodeResult.Error(RuleCodeError.BAD_BASE64)
            else -> fromJson(bytes.toString(Charsets.UTF_8))
        }
    }

    /** Reads one rule from JSON text. */
    fun fromJson(text: String): RuleCodeResult =
        jsonObject(text)?.let(::fromJson) ?: RuleCodeResult.Error(RuleCodeError.BAD_JSON)

    /**
     * The version is checked before the rest, so a rule written for a newer format gets
     * [RuleCodeError.UNKNOWN_VERSION] rather than a confusing field error.
     */
    internal fun fromJson(element: JsonObject): RuleCodeResult {
        val version = (element["v"] as? JsonPrimitive)?.intOrNull
        val rule = if (version == RuleFormat.VERSION) decodeRule(element) else null
        return when {
            version != RuleFormat.VERSION -> RuleCodeResult.Error(RuleCodeError.UNKNOWN_VERSION)
            rule == null -> RuleCodeResult.Error(RuleCodeError.BAD_JSON)
            else -> RuleCodeResult.Decoded(rule)
        }
    }

    // kotlinx.serialization's SerializationException is an IllegalArgumentException.
    private fun base64(text: String): ByteArray? = try {
        Base64.getUrlDecoder().decode(text)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun jsonObject(text: String): JsonObject? = try {
        json.parseToJsonElement(text) as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun decodeRule(element: JsonObject): ParserRule? = try {
        json.decodeFromJsonElement(ParserRule.serializer(), element)
    } catch (_: IllegalArgumentException) {
        null
    }
}

sealed interface RuleCodeResult {
    data class Decoded(val rule: ParserRule) : RuleCodeResult

    data class Error(val error: RuleCodeError) : RuleCodeResult
}

/** Stable codes: the app maps them to translated messages (docs/parser-rules.md lists them). */
enum class RuleCodeError(val code: String) {
    BAD_PREFIX("bad_prefix"),
    BAD_BASE64("bad_base64"),
    BAD_JSON("bad_json"),
    UNKNOWN_VERSION("unknown_version")
}
