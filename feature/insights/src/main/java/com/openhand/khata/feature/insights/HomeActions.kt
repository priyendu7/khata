package com.openhand.khata.feature.insights

/**
 * Where Home's cards lead. The two "See all" links switch tabs, so they come from the tab shell
 * rather than the navigation graph.
 */
class HomeActions(
    val onReview: () -> Unit = {},
    val onBackup: () -> Unit = {},
    val onOpenTransaction: (Long) -> Unit = {},
    val onAddTransaction: () -> Unit = {},
    val onTurnOnSms: () -> Unit = {},
    val onSeeAllTransactions: () -> Unit = {},
    val onSeeAllInsights: () -> Unit = {}
)
