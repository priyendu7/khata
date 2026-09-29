package com.openhand.khata.sms.parser

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
    fun load(): List<CompiledRule> = BANKS.flatMap { bank ->
        rules(bank).map { rule ->
            when (val check = RuleValidator.validate(rule)) {
                is RuleCheck.Valid -> check.rule
                is RuleCheck.Invalid -> error(
                    "Built-in rule ${rule.id} is invalid: ${check.errors}"
                )
            }
        }
    }

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
