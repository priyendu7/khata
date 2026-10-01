package com.openhand.khata.core.model

/**
 * An ignore rule the user made from an SMS no parser read (PRD feature 7), listed in Settings >
 * SMS import > Filters.
 */
data class SmsIgnoreRule(
    val id: Long,
    val kind: IgnoreKind,
    /** The sender header, e.g. `HDFCBK` for `VM-HDFCBK-S`. */
    val header: String,
    /** For [IgnoreKind.TEMPLATE]: the pattern of messages like the sample. Null for a sender. */
    val pattern: String?,
    /** The SMS it was made from, shown in the list. */
    val sample: String,
    val enabled: Boolean,
    val createdAt: Long
)

enum class IgnoreKind {
    /** Every SMS from the sender, dropped before its text is read. */
    SENDER,

    /** SMS like the sample, only when no parser read them. */
    TEMPLATE
}
