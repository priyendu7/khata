package com.openhand.khata.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.openhand.khata.R
import com.openhand.khata.feature.accounts.AccountsScreen
import com.openhand.khata.feature.categories.CategoriesScreen
import com.openhand.khata.feature.categories.TagsScreen
import com.openhand.khata.feature.csv.ExportScreen
import com.openhand.khata.feature.csv.ImportScreen
import com.openhand.khata.feature.insights.HomeScreen
import com.openhand.khata.feature.insights.InsightsScreen
import com.openhand.khata.feature.lock.LockSettingsSection
import com.openhand.khata.feature.payees.PayeesScreen
import com.openhand.khata.feature.settings.SettingsPage
import com.openhand.khata.feature.settings.SettingsRoute
import com.openhand.khata.feature.transactions.AddTransactionButton
import com.openhand.khata.feature.transactions.FILTER_CATEGORY_ARG
import com.openhand.khata.feature.transactions.FILTER_FROM_ARG
import com.openhand.khata.feature.transactions.FILTER_UNTIL_ARG
import com.openhand.khata.feature.transactions.NO_FILTER
import com.openhand.khata.feature.transactions.TRANSACTION_ID_ARG
import com.openhand.khata.feature.transactions.TransactionEditorScreen
import com.openhand.khata.feature.transactions.TransactionsScreen

/** Top-level navigation: the tabs, and the screens opened from them. */
@Composable
fun KhataNavigation() {
    val navController = rememberNavController()
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(navController, startDestination = Route.TABS) {
        composable(Route.TABS) {
            MainTabs(
                onOpen = { page -> navController.navigate(page.route()) },
                onOpenTransaction = { id -> navController.navigate(Route.transaction(id)) },
                onOpenTransactions = { categoryId, from, until ->
                    navController.navigate(Route.transactions(categoryId, from, until))
                }
            )
        }
        composable(
            Route.TRANSACTIONS,
            arguments = listOf(FILTER_CATEGORY_ARG, FILTER_FROM_ARG, FILTER_UNTIL_ARG).map {
                navArgument(it) {
                    type = NavType.LongType
                    defaultValue = NO_FILTER
                }
            }
        ) {
            TransactionsScreen(
                onOpen = { id -> navController.navigate(Route.transaction(id)) },
                onBack = back
            )
        }
        composable(
            Route.TRANSACTION,
            arguments = listOf(navArgument(TRANSACTION_ID_ARG) { type = NavType.LongType })
        ) { TransactionEditorScreen(onDone = back) }
        composable(Route.ACCOUNTS) { AccountsScreen(onBack = back) }
        composable(Route.CATEGORIES) { CategoriesScreen(onBack = back) }
        composable(Route.TAGS) { TagsScreen(onBack = back) }
        composable(Route.PAYEES) { PayeesScreen(onBack = back) }
        composable(Route.EXPORT) { ExportScreen(onBack = back) }
        composable(Route.IMPORT) { ImportScreen(onBack = back) }
    }
}

private object Route {
    const val TABS = "tabs"
    const val ACCOUNTS = "accounts"
    const val CATEGORIES = "categories"
    const val TAGS = "tags"
    const val PAYEES = "payees"
    const val EXPORT = "export"
    const val IMPORT = "import"

    /** Add (id 0) or edit a transaction. */
    const val TRANSACTION = "transaction/{$TRANSACTION_ID_ARG}"

    fun transaction(id: Long) = "transaction/$id"

    /** The transactions list, filtered by a category (null for any) and a date range. */
    const val TRANSACTIONS = "transactions?$FILTER_CATEGORY_ARG={$FILTER_CATEGORY_ARG}" +
        "&$FILTER_FROM_ARG={$FILTER_FROM_ARG}&$FILTER_UNTIL_ARG={$FILTER_UNTIL_ARG}"

    fun transactions(categoryId: Long?, from: Long, until: Long) =
        "transactions?$FILTER_CATEGORY_ARG=${categoryId ?: NO_FILTER}" +
            "&$FILTER_FROM_ARG=$from&$FILTER_UNTIL_ARG=$until"
}

private fun SettingsPage.route() = when (this) {
    SettingsPage.ACCOUNTS -> Route.ACCOUNTS
    SettingsPage.CATEGORIES -> Route.CATEGORIES
    SettingsPage.TAGS -> Route.TAGS
    SettingsPage.PAYEES -> Route.PAYEES
    SettingsPage.EXPORT -> Route.EXPORT
    SettingsPage.IMPORT -> Route.IMPORT
}

/** Bottom-navigation shell: Home, Transactions, Insights, Settings. */
@Composable
private fun MainTabs(
    onOpen: (SettingsPage) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenTransactions: (categoryId: Long?, from: Long, until: Long) -> Unit
) {
    var current by rememberSaveable { mutableStateOf(Destination.HOME) }
    Scaffold(
        floatingActionButton = {
            if (current == Destination.HOME || current == Destination.TRANSACTIONS) {
                AddTransactionButton(onClick = { onOpenTransaction(0L) })
            }
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == current,
                        onClick = { current = destination },
                        icon = {
                            Icon(
                                painterResource(destination.icon),
                                contentDescription = null
                            )
                        },
                        label = { Text(stringResource(destination.label)) }
                    )
                }
            }
        }
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (current) {
            Destination.HOME -> HomeScreen(stringResource(R.string.app_name), modifier)
            Destination.TRANSACTIONS -> TransactionsScreen(onOpenTransaction, modifier)
            Destination.INSIGHTS -> InsightsScreen(onOpenTransactions, modifier)
            Destination.SETTINGS -> SettingsRoute(modifier, lockSettings = {
                LockSettingsSection()
            }, onOpen = onOpen)
        }
    }
}
