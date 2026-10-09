package com.openhand.khata.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.ui.Choice
import com.openhand.khata.core.ui.ChoiceDialog
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.ScreenTitle
import com.openhand.khata.core.ui.SectionHeader
import com.openhand.khata.core.ui.SegmentListItem
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.segment

/** Settings tab, wired to its [SettingsViewModel] through Hilt. */
@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    security: @Composable () -> Unit = {},
    onOpen: (SettingsPage) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    // Changing it recreates the activity, so this is read afresh in the new language.
    var language by remember { mutableStateOf(AppLanguage.current()) }
    SettingsScreen(
        versionName = viewModel.versionName,
        modifier = modifier,
        summary = summary,
        security = security,
        backupReminder = { BackupReminderSection(it) },
        onOpen = onOpen,
        // The browser does the fetching; Khata itself has no internet permission.
        onOpenLink = { runCatching { uriHandler.openUri(it) } },
        language = language,
        onLanguage = {
            language = it
            it.applyToApp()
        }
    )
}

/** Screens that Settings opens; :app maps them to navigation routes. */
enum class SettingsPage {
    SMS_IMPORT,
    FILTERS,
    TEST_MESSAGE,
    PARSERS,
    ACCOUNTS,
    CATEGORIES,
    TAGS,
    EVENTS,
    PAYEES,
    EXPORT,
    IMPORT,
    EXPORT_SETTINGS,
    IMPORT_SETTINGS,
    LOCK,
    PROMISE
}

@Composable
fun SettingsScreen(
    versionName: String,
    modifier: Modifier = Modifier,
    summary: SettingsSummary = SettingsSummary(),
    /** App lock and Block screenshots, supplied by :feature:lock through :app. */
    security: @Composable () -> Unit = {},
    /** Its own ViewModel, so it's a slot that tests can leave empty. Drawn as the last segment. */
    backupReminder: @Composable (Modifier) -> Unit = {},
    onOpen: (SettingsPage) -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    language: AppLanguage = AppLanguage.SYSTEM,
    onLanguage: (AppLanguage) -> Unit = {}
) {
    var choosingLanguage by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(stringResource(UiR.string.nav_settings))
        // Section names line up with the text of the rows below them.
        Column(Modifier.padding(start = Segments.Inset, end = Segments.Inset, bottom = 16.dp)) {
            SmsSection(summary, onOpen)
            ListsSection(summary, onOpen)
            BackupSection(summary, onOpen, backupReminder)
            SectionHeader(stringResource(R.string.settings_section_security))
            security()
            SectionHeader(stringResource(R.string.settings_section_app))
            Row(stringResource(R.string.settings_language), languageName(language), 0, 1) {
                choosingLanguage = true
            }
            AboutSection(versionName, onOpen, onOpenLink)
        }
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

@Composable
private fun SmsSection(summary: SettingsSummary, onOpen: (SettingsPage) -> Unit) {
    SectionHeader(stringResource(R.string.settings_section_sms))
    val onOff =
        stringResource(if (summary.smsEnabled) R.string.settings_on else R.string.settings_off)
    Row(
        stringResource(R.string.settings_sms),
        summary.lastScan?.let { stringResource(R.string.settings_sms_scanned, onOff, date(it)) }
            ?: onOff,
        0,
        SMS_ROWS
    ) { onOpen(SettingsPage.SMS_IMPORT) }
    Row(
        stringResource(R.string.filters_title),
        pair(
            count(R.plurals.settings_filters_on, summary.filtersOn),
            count(R.plurals.settings_ignore_rules, summary.ignoreRules)
        ),
        1,
        SMS_ROWS
    ) { onOpen(SettingsPage.FILTERS) }
    Row(
        stringResource(R.string.test_sms_title),
        stringResource(R.string.settings_test_value),
        2,
        SMS_ROWS
    ) { onOpen(SettingsPage.TEST_MESSAGE) }
    Row(
        stringResource(R.string.settings_parsers),
        pair(
            count(R.plurals.settings_parsers_builtin, summary.builtInParsers),
            count(R.plurals.settings_parsers_custom, summary.customParsers)
        ),
        3,
        SMS_ROWS
    ) { onOpen(SettingsPage.PARSERS) }
}

@Composable
private fun ListsSection(summary: SettingsSummary, onOpen: (SettingsPage) -> Unit) {
    SectionHeader(stringResource(R.string.settings_section_lists))
    Row(
        stringResource(R.string.settings_accounts),
        count(R.plurals.settings_accounts_count, summary.accounts),
        0,
        LIST_ROWS
    ) { onOpen(SettingsPage.ACCOUNTS) }
    Row(
        stringResource(R.string.settings_categories),
        count(R.plurals.settings_categories_count, summary.categories),
        1,
        LIST_ROWS
    ) { onOpen(SettingsPage.CATEGORIES) }
    Row(
        stringResource(R.string.settings_tags),
        count(R.plurals.settings_tags_count, summary.tags),
        2,
        LIST_ROWS
    ) { onOpen(SettingsPage.TAGS) }
    Row(
        stringResource(R.string.settings_payees),
        count(R.plurals.settings_payees_count, summary.payees),
        3,
        LIST_ROWS
    ) { onOpen(SettingsPage.PAYEES) }
    val events = count(R.plurals.settings_events_count, summary.events)
    Row(
        stringResource(R.string.settings_events),
        summary.newestEvent?.let { pair(events, it) } ?: events,
        4,
        LIST_ROWS
    ) { onOpen(SettingsPage.EVENTS) }
}

@Composable
private fun BackupSection(
    summary: SettingsSummary,
    onOpen: (SettingsPage) -> Unit,
    backupReminder: @Composable (Modifier) -> Unit
) {
    SectionHeader(stringResource(R.string.settings_section_backup))
    Row(
        stringResource(R.string.settings_export),
        summary.lastExport?.let { stringResource(R.string.settings_export_last, date(it)) }
            ?: stringResource(R.string.settings_export_never),
        0,
        BACKUP_ROWS
    ) { onOpen(SettingsPage.EXPORT) }
    Row(
        stringResource(R.string.settings_import),
        stringResource(R.string.settings_import_value),
        1,
        BACKUP_ROWS
    ) { onOpen(SettingsPage.IMPORT) }
    Row(
        stringResource(R.string.settings_export_settings),
        stringResource(R.string.settings_export_settings_value),
        2,
        BACKUP_ROWS
    ) { onOpen(SettingsPage.EXPORT_SETTINGS) }
    Row(
        stringResource(R.string.settings_import_settings),
        stringResource(R.string.settings_import_settings_value),
        3,
        BACKUP_ROWS
    ) { onOpen(SettingsPage.IMPORT_SETTINGS) }
    backupReminder(Modifier.segment(BACKUP_ROWS - 1, BACKUP_ROWS))
}

@Composable
private fun AboutSection(
    versionName: String,
    onOpen: (SettingsPage) -> Unit,
    onOpenLink: (String) -> Unit
) {
    SectionHeader(stringResource(R.string.settings_section_about))
    Row(
        stringResource(R.string.promise_title),
        stringResource(R.string.settings_promise_value),
        0,
        ABOUT_ROWS
    ) { onOpen(SettingsPage.PROMISE) }
    Row(
        stringResource(R.string.settings_privacy),
        stringResource(R.string.settings_privacy_value),
        1,
        ABOUT_ROWS
    ) { onOpenLink(PRIVACY_POLICY_URL) }
    Row(
        stringResource(R.string.settings_source),
        stringResource(R.string.settings_source_value),
        2,
        ABOUT_ROWS
    ) { onOpenLink(SOURCE_URL) }
    Row(stringResource(R.string.settings_version), versionName, 3, ABOUT_ROWS)
}

/** English and हिन्दी are always written in their own script, so either can be found. */
@Composable
internal fun languageName(language: AppLanguage) = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.ENGLISH -> R.string.language_english
        AppLanguage.HINDI -> R.string.language_hindi
    }
)

@Composable
private fun Row(
    title: String,
    value: String,
    index: Int,
    count: Int,
    onClick: (() -> Unit)? = null
) {
    SegmentListItem(
        index = index,
        count = count,
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
}

// The number of rows in each section, for their segment shapes.
private const val SMS_ROWS = 4
private const val LIST_ROWS = 5
private const val BACKUP_ROWS = 5
private const val ABOUT_ROWS = 4

private const val PRIVACY_POLICY_URL =
    "https://github.com/priyendu7/khata/blob/main/docs/PRIVACY.md"
