package com.openhand.khata.feature.settings

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
fun SettingsRoute(modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    SettingsScreen(versionName = viewModel.versionName, modifier = modifier)
}

// TODO(Phase 0-3): app lock, language switch, backup reminder and parsers become real settings.
@Composable
fun SettingsScreen(versionName: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(stringResource(UiR.string.nav_settings))
        Row(
            stringResource(R.string.settings_privacy),
            stringResource(R.string.settings_privacy_value)
        )
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
private fun Row(title: String, value: String) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(value) })
}
