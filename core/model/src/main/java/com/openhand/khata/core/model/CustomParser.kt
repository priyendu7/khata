package com.openhand.khata.core.model

/** A parser rule the user added in Settings > Parsers, kept as its rule code (`khata1:…`). */
data class CustomParser(
    val id: Long,
    val ruleId: String,
    val bank: String,
    val code: String,
    val enabled: Boolean,
    val addedAt: Long
)
