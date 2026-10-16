package com.openhand.khata.feature.insights

/**
 * What a credit card's page does: show a statement's cycle, set the billing day and reminders,
 * open spending, and see, link or record payments (#166).
 */
class CreditCardActions(
    val onBack: () -> Unit = {},
    /** A statement's id, or null for the cycle running now. */
    val onShowStatement: (Long?) -> Unit = {},
    val onSetBillingDay: (Int?) -> Unit = {},
    /** Turns this card's due date reminders (#156) on or off. */
    val onSetReminders: (Boolean) -> Unit = {},
    /** A category's id, or null for "Other". */
    val onOpenCategory: (Long?) -> Unit = {},
    /** Opens a payment in the transaction editor, by its id. */
    val onOpenPayment: (Long) -> Unit = {},
    /** Links a "card not known" bill payment to this card, by its id. */
    val onLinkPayment: (Long) -> Unit = {},
    /** Unlinks a payment "Add a payment" linked, by its card side's id. */
    val onUnlinkPayment: (Long) -> Unit = {},
    /** Opens the editor for a new payment into this card, of the current statement's total. */
    val onRecordPayment: () -> Unit = {}
)
