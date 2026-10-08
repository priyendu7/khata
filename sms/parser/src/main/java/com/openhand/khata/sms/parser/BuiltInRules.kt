package com.openhand.khata.sms.parser

import java.security.MessageDigest
import kotlinx.serialization.builtins.ListSerializer

/**
 * The rules that ship with the app, one JSON file per bank in `src/main/resources/rules/`, in the
 * same format as custom rules (docs/parser-rules.md). Adding a bank means adding its file here and
 * its samples in `src/test/resources/samples/`.
 */
object BuiltInRules {
    /** In the order they're tried. Resources can't be listed on Android, so they're named here. */
    internal val BANKS = listOf("kotak")

    /** Every built-in rule, checked. A rule that fails its checks is a bug, so it throws. */
    fun load(): List<CompiledRule> = all().map { rule ->
        when (val check = RuleValidator.validate(rule)) {
            is RuleCheck.Valid -> check.rule
            is RuleCheck.Invalid -> error("Built-in rule ${rule.id} is invalid: ${check.errors}")
        }
    }

    /** Every built-in rule as shipped, in the order they're tried. */
    fun all(): List<ParserRule> = BANKS.flatMap(::rules)

    /**
     * [rules] with the user's changes (Settings > Parsers): a rule switched off is dropped, and an
     * edited one runs in its place. An edit that no longer passes the checks, or names another
     * rule id, falls back to the original. Overrides for ids not in [rules] are ignored.
     */
    fun withOverrides(
        rules: List<CompiledRule>,
        overrides: List<RuleOverride>
    ): List<CompiledRule> {
        val byId = overrides.associateBy { it.ruleId }
        return rules.mapNotNull { original ->
            val override = byId[original.rule.id]
            when {
                override == null -> original
                !override.enabled -> null
                else -> override.editedCode?.let { edited(original.rule.id, it) } ?: original
            }
        }
    }

    /**
     * A stable hash of [rule]'s JSON, stored with an edit so that an app update that changed the
     * built-in rule since can be noticed.
     */
    fun hash(rule: ParserRule): String {
        val json = RuleCode.json.encodeToString(ParserRule.serializer(), rule)
        return MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun edited(id: String, code: String): CompiledRule? =
        (CustomRules.check(code) as? CodeCheck.Valid)?.rule?.takeIf { it.rule.id == id }

    internal fun rules(bank: String): List<ParserRule> {
        val path = "/rules/$bank.json"
        val text = requireNotNull(BuiltInRules::class.java.getResourceAsStream(path)) {
            "Missing $path"
        }
            .bufferedReader()
            .use { it.readText() }
        return RuleCode.json.decodeFromString(ListSerializer(ParserRule.serializer()), text)
    }
}

/**
 * The user's change to a built-in rule: switched off, or edited ([editedCode] is its rule code).
 * The rules themselves ship inside the app, so the change is stored apart and applied on top.
 */
data class RuleOverride(val ruleId: String, val enabled: Boolean, val editedCode: String?)
