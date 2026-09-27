package com.openhand.khata.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.ScreenTitle

/** Settings tab, wired to its [SettingsViewModel] through Hilt. */
@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    lockSettings: @Composable () -> Unit = {},
    onOpen: (SettingsPage) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    SettingsScreen(
        versionName = viewModel.versionName,
        modifier = modifier,
        lockSettings = lockSettings,
        onOpen = onOpen
    )
}

/** Screens that Settings opens; :app maps them to navigation routes. */
enum class SettingsPage { ACCOUNTS, CATEGORIES, TAGS, PAYEES }

// TODO(Phase 1-3): language switch, backup reminder and parsers become real settings.
@Composable
fun SettingsScreen(
    versionName: String,
    modifier: Modifier = Modifier,
    /** App lock rows, supplied by :feature:lock through :app (features don't depend on each other). */
    lockSettings: @Composable () -> Unit = {},
    onOpen: (SettingsPage) -> Unit = {}
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(stringResource(UiR.string.nav_settings))
        Row(
            stringResource(R.string.settings_accounts),
            stringResource(R.string.settings_accounts_value)
        ) {
            onOpen(SettingsPage.ACCOUNTS)
        }
        Row(
            stringResource(R.string.settings_categories),
            stringResource(R.string.settings_categories_value)
        ) {
            onOpen(SettingsPage.CATEGORIES)
        }
        Row(stringResource(R.string.settings_tags), stringResource(R.string.settings_tags_value)) {
            onOpen(SettingsPage.TAGS)
        }
        Row(
            stringResource(R.string.settings_payees),
            stringResource(R.string.settings_payees_value)
        ) {
            onOpen(SettingsPage.PAYEES)
        }
        HorizontalDivider()
        Row(
            stringResource(R.string.settings_privacy),
            stringResource(R.string.settings_privacy_value)
        )
        lockSettings()
        Row(
            stringResource(R.string.settings_language),
            stringResource(R.string.settings_language_value)
        )
        Row(
            stringResource(R.string.settings_backup),
            stringResource(R.string.settings_backup_value)
        )
        HorizontalDivider()
        Row(
            stringResource(R.string.settings_source),
            stringResource(R.string.settings_source_value)
        )
        Row(stringResource(R.string.settings_version), versionName)
    }
}

@Composable
private fun Row(title: String, value: String, onClick: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
}
