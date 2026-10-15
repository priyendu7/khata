package com.openhand.khata.feature.insights

/**
 * What a credit card's page does: show a statement's cycle, set the billing day and reminders,
 * open spending.
 */
class CreditCardActions(
    val onBack: () -> Unit = {},
    /** A statement's id, or null for the cycle running now. */
    val onShowStatement: (Long?) -> Unit = {},
    val onSetBillingDay: (Int?) -> Unit = {},
    /** Turns this card's due date reminders (#156) on or off. */
    val onSetReminders: (Boolean) -> Unit = {},
    /** A category's id, or null for "Other". */
    val onOpenCategory: (Long?) -> Unit = {}
)
