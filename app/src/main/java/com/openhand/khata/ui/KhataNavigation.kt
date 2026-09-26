package com.openhand.khata.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import com.openhand.khata.R
import com.openhand.khata.feature.insights.HomeScreen
import com.openhand.khata.feature.insights.InsightsScreen
import com.openhand.khata.feature.settings.SettingsScreen
import com.openhand.khata.feature.transactions.TransactionsScreen

enum class Destination(
    @StringRes val label: Int,
    @DrawableRes val icon: Int
) {
    HOME(R.string.nav_home, R.drawable.ic_home),
    TRANSACTIONS(R.string.nav_transactions, R.drawable.ic_ledger),
    INSIGHTS(R.string.nav_insights, R.drawable.ic_insights),
    SETTINGS(R.string.nav_settings, R.drawable.ic_settings)
}

/** Bottom-navigation shell from the development plan (Phase 0): Home, Transactions, Insights, Settings. */
@Composable
fun KhataNavigation() {
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
            Destination.HOME -> HomeScreen(modifier)
            Destination.TRANSACTIONS -> TransactionsScreen(modifier)
            Destination.INSIGHTS -> InsightsScreen(modifier)
            Destination.SETTINGS -> SettingsScreen(modifier)
        }
    }
}
