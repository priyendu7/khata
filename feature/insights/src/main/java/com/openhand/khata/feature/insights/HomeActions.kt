package com.openhand.khata.feature.insights

import com.openhand.khata.core.model.TransactionFilter

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
    val onSeeAllInsights: () -> Unit = {},
    /** A credit card's page, by its id. */
    val onOpenCard: (Long) -> Unit = {},
    /** The transactions list with a filter, such as this month's income (#164). */
    val onOpenTransactions: (TransactionFilter) -> Unit = {}
)
