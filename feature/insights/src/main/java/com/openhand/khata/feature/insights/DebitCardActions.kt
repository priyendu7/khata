package com.openhand.khata.feature.insights

/** What a debit card's page does: link it, change the period, open spending. */
class DebitCardActions(
    val onBack: () -> Unit = {},
    /** A bank account's id, or null to unlink. */
    val onLink: (Long?) -> Unit = {},
    val period: CardActions<Long?> = CardActions()
)
