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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.openhand.khata.core.ui.Choice
import com.openhand.khata.core.ui.ChoiceDialog
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
    // Changing it recreates the activity, so this is read afresh in the new language.
    var language by remember { mutableStateOf(AppLanguage.current()) }
    SettingsScreen(
        versionName = viewModel.versionName,
        modifier = modifier,
        lockSettings = lockSettings,
        backupReminder = { BackupReminderSection() },
        onOpen = onOpen,
        language = language,
        onLanguage = {
            language = it
            it.applyToApp()
        }
    )
}

/** Screens that Settings opens; :app maps them to navigation routes. */
enum class SettingsPage { ACCOUNTS, CATEGORIES, TAGS, PAYEES, EXPORT, IMPORT }

// TODO(Phase 3): parsers become real settings.
@Composable
fun SettingsScreen(
    versionName: String,
    modifier: Modifier = Modifier,
    /** App lock rows, supplied by :feature:lock through :app (features don't depend on each other). */
    lockSettings: @Composable () -> Unit = {},
    /** Its own ViewModel, so it's a slot that tests can leave empty. */
    backupReminder: @Composable () -> Unit = {},
    onOpen: (SettingsPage) -> Unit = {},
    language: AppLanguage = AppLanguage.SYSTEM,
    onLanguage: (AppLanguage) -> Unit = {}
) {
    var choosingLanguage by rememberSaveable { mutableStateOf(false) }
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
        Row(stringResource(R.string.settings_language), languageName(language)) {
            choosingLanguage = true
        }
        Row(
            stringResource(R.string.settings_export),
            stringResource(R.string.settings_export_value)
        ) {
            onOpen(SettingsPage.EXPORT)
        }
        Row(
            stringResource(R.string.settings_import),
            stringResource(R.string.settings_import_value)
        ) {
            onOpen(SettingsPage.IMPORT)
        }
        backupReminder()
        HorizontalDivider()
        Row(
            stringResource(R.string.settings_source),
            stringResource(R.string.settings_source_value)
        )
        Row(stringResource(R.string.settings_version), versionName)
    }
    if (choosingLanguage) {
        ChoiceDialog(
            title = stringResource(R.string.settings_language),
            choices = AppLanguage.entries.map { Choice(it, languageName(it)) },
            selected = language,
            onSelect = {
                choosingLanguage = false
                if (it != language) onLanguage(it)
            },
            onDismiss = { choosingLanguage = false }
        )
    }
}

/** English and हिन्दी are always written in their own script, so either can be found. */
@Composable
private fun languageName(language: AppLanguage) = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.ENGLISH -> R.string.language_english
        AppLanguage.HINDI -> R.string.language_hindi
    }
)

@Composable
private fun Row(title: String, value: String, onClick: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
}
