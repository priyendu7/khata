package com.openhand.khata.feature.insights

/**
 * What a card with its own period does, such as the tag or account card. [onStepPeriod] gets -1
 * for back and 1 for forward, [onSelectPast] how many periods back; [onOpen] gets the tapped
 * tag or account, null for Untagged or No account.
 */
class CardActions<T>(
    val onSelectPeriod: (ChartPeriod) -> Unit = {},
    val onStepPeriod: (Int) -> Unit = {},
    val onSelectPast: (Int) -> Unit = {},
    val onOpen: (T) -> Unit = {}
)
