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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.openhand.khata.R
import com.openhand.khata.feature.accounts.AccountsScreen
import com.openhand.khata.feature.categories.CategoriesScreen
import com.openhand.khata.feature.categories.TagsScreen
import com.openhand.khata.feature.insights.HomeScreen
import com.openhand.khata.feature.insights.InsightsScreen
import com.openhand.khata.feature.lock.LockSettingsSection
import com.openhand.khata.feature.settings.SettingsPage
import com.openhand.khata.feature.settings.SettingsRoute
import com.openhand.khata.feature.transactions.TransactionsScreen

/** Top-level navigation: the tabs, and the screens opened from them. */
@Composable
fun KhataNavigation() {
    val navController = rememberNavController()
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(navController, startDestination = Route.TABS) {
        composable(Route.TABS) {
            MainTabs(onOpen = { page -> navController.navigate(page.route()) })
        }
        composable(Route.ACCOUNTS) { AccountsScreen(onBack = back) }
        composable(Route.CATEGORIES) { CategoriesScreen(onBack = back) }
        composable(Route.TAGS) { TagsScreen(onBack = back) }
    }
}

private object Route {
    const val TABS = "tabs"
    const val ACCOUNTS = "accounts"
    const val CATEGORIES = "categories"
    const val TAGS = "tags"
}

private fun SettingsPage.route() = when (this) {
    SettingsPage.ACCOUNTS -> Route.ACCOUNTS
    SettingsPage.CATEGORIES -> Route.CATEGORIES
    SettingsPage.TAGS -> Route.TAGS
}

/** Bottom-navigation shell: Home, Transactions, Insights, Settings. */
@Composable
private fun MainTabs(onOpen: (SettingsPage) -> Unit) {
    var current by rememberSaveable { mutableStateOf(Destination.HOME) }
    Scaffold(
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
            Destination.TRANSACTIONS -> TransactionsScreen(modifier)
            Destination.INSIGHTS -> InsightsScreen(modifier)
            Destination.SETTINGS -> SettingsRoute(modifier, lockSettings = {
                LockSettingsSection()
            }, onOpen = onOpen)
        }
    }
}
