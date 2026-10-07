package com.openhand.khata.feature.insights

import com.openhand.khata.core.model.Tag
import com.openhand.khata.core.model.TransactionFilter

/**
 * What the tag card and its breakdown sheet do. [onStepPeriod] gets -1 for back and 1 for
 * forward, [onSelectPast] how many periods back; [onOpenTag] gets null for Untagged.
 */
class TagActions(
    val onSelectPeriod: (ChartPeriod) -> Unit = {},
    val onStepPeriod: (Int) -> Unit = {},
    val onSelectPast: (Int) -> Unit = {},
    val onOpenTag: (Tag?) -> Unit = {},
    val onCloseTag: () -> Unit = {},
    val onOpenTransactions: (TransactionFilter) -> Unit = {}
)
