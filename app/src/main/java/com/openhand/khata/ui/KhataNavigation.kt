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
import com.openhand.khata.R
import com.openhand.khata.feature.insights.HomeScreen
import com.openhand.khata.feature.insights.InsightsScreen
import com.openhand.khata.feature.lock.LockSettingsSection
import com.openhand.khata.feature.settings.SettingsRoute
import com.openhand.khata.feature.transactions.TransactionsScreen

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
            Destination.HOME -> HomeScreen(stringResource(R.string.app_name), modifier)
            Destination.TRANSACTIONS -> TransactionsScreen(modifier)
            Destination.INSIGHTS -> InsightsScreen(modifier)
            Destination.SETTINGS -> SettingsRoute(modifier, lockSettings = {
                LockSettingsSection()
            })
        }
    }
}
