package com.openhand.khata.core.model

/**
 * The user's change to a built-in parser rule: switched off, or edited ([editedCode] is the edited
 * rule's code, `khata1:…`). [baseHash] is the built-in rule's hash when it was edited.
 */
data class BuiltInRuleOverride(
    val ruleId: String,
    val enabled: Boolean,
    val editedCode: String?,
    val baseHash: String?
)
