package com.openhand.khata.sms.parser

/**
 * Rules the user pastes in Settings > Parsers (PRD feature 8), as rule codes ([RuleCode]). They
 * go through the same checks as built-in rules, and the engine tries them first, so a custom rule
 * can fix a built-in format that has stopped working.
 */
object CustomRules {
    /** Reads a pasted code and checks the rule in it. */
    fun check(code: String): CodeCheck = when (val decoded = RuleCode.decode(code)) {
        is RuleCodeResult.Error -> CodeCheck.Unreadable(decoded.error)
        is RuleCodeResult.Decoded -> when (val check = RuleValidator.validate(decoded.rule)) {
            is RuleCheck.Valid -> CodeCheck.Valid(check.rule)
            is RuleCheck.Invalid -> CodeCheck.Invalid(check.errors)
        }
    }

    /**
     * Saved codes as rules, in the order given. A code that no longer passes (a later version of
     * the checks may be stricter) is skipped rather than stopping SMS import.
     */
    fun load(codes: List<String>): List<CompiledRule> =
        codes.mapNotNull { (check(it) as? CodeCheck.Valid)?.rule }

    /** The engine's order: custom rules first, then built-in ones. */
    fun parser(
        custom: List<CompiledRule>,
        builtIn: List<CompiledRule>,
        filters: SmsFilters = SmsFilters(),
        ignore: IgnoreRules = IgnoreRules()
    ): SmsParser = SmsParser(custom + builtIn, filters = filters, ignore = ignore)
}

sealed interface CodeCheck {
    data class Valid(val rule: CompiledRule) : CodeCheck

    /** Not a rule code at all, or one for a newer version of Khata. */
    data class Unreadable(val error: RuleCodeError) : CodeCheck

    /** A readable rule that fails [RuleValidator]. */
    data class Invalid(val errors: List<RuleError>) : CodeCheck
}
