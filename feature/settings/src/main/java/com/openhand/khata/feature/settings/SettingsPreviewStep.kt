package com.openhand.khata.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.data.MatchCounts
import com.openhand.khata.core.data.RuleComparison
import com.openhand.khata.core.data.RuleStatus

/** What importing a settings file will change, before anything is saved. */
@Composable
internal fun SettingsPreviewStep(
    state: SettingsImportState.Preview,
    onKeep: (ruleId: String, builtIn: Boolean, keep: Boolean) -> Unit,
    onKeepAll: (keep: Boolean) -> Unit,
    onImport: () -> Unit
) {
    val preview = state.preview
    Text(stringResource(R.string.import_settings_preview_intro))
    if ((preview.customRules + preview.builtInEdits).any { it.status == RuleStatus.CHANGED }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onKeepAll(false) }) {
                Text(stringResource(R.string.import_settings_replace_all))
            }
            TextButton(onClick = { onKeepAll(true) }) {
                Text(stringResource(R.string.import_settings_keep_all))
            }
        }
    }
    Section(stringResource(R.string.import_settings_custom_rules)) {
        RuleRows(preview.customRules, state.choices.keepCustom) { id, keep ->
            onKeep(id, false, keep)
        }
        if (preview.unreadableCustomRules > 0) {
            PreviewNote(
                pluralStringResource(
                    R.plurals.import_settings_unreadable_rules,
                    preview.unreadableCustomRules,
                    preview.unreadableCustomRules
                )
            )
        }
    }
    Section(stringResource(R.string.import_settings_builtin_edits)) {
        RuleRows(preview.builtInEdits, state.choices.keepBuiltIn) { id, keep ->
            onKeep(id, true, keep)
        }
        if (preview.olderBuiltIns.isNotEmpty()) {
            PreviewNote(
                stringResource(
                    R.string.import_settings_older_builtins,
                    preview.olderBuiltIns.joinToString()
                )
            )
        }
        if (preview.unknownBuiltIns.isNotEmpty()) {
            PreviewNote(
                stringResource(
                    R.string.import_settings_unknown_builtins,
                    preview.unknownBuiltIns.joinToString()
                )
            )
        }
    }
    Section(stringResource(R.string.import_settings_setup)) {
        CountsRow(R.string.import_settings_ignore_rules, preview.ignoreRules)
        CountsRow(R.string.settings_categories, preview.categories)
        CountsRow(R.string.settings_accounts, preview.accounts)
        CountsRow(R.string.settings_payees, preview.payees)
        CountsRow(R.string.settings_events, preview.events)
    }
    Section(stringResource(R.string.import_settings_preferences)) {
        PreferenceRows(state.current, state.file)
    }
    Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.import_settings_import))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
internal fun PreviewNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun RuleRows(
    rules: List<RuleComparison>,
    kept: Set<String>,
    onKeep: (ruleId: String, keep: Boolean) -> Unit
) {
    if (rules.isEmpty()) PreviewNote(stringResource(R.string.import_settings_none))
    rules.forEach { rule ->
        ListItem(
            headlineContent = { Text(rule.ruleId) },
            supportingContent = { Text(ruleStatus(rule)) }
        )
        if (rule.status == RuleStatus.CHANGED) {
            val keep = rule.ruleId in kept
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !keep,
                    onClick = { onKeep(rule.ruleId, false) },
                    label = { Text(stringResource(R.string.import_settings_replace)) }
                )
                FilterChip(
                    selected = keep,
                    onClick = { onKeep(rule.ruleId, true) },
                    label = { Text(stringResource(R.string.import_settings_keep_mine)) }
                )
            }
        }
    }
}

@Composable
private fun ruleStatus(rule: RuleComparison): String {
    val onOff = stringResource(if (rule.enabled) R.string.settings_on else R.string.settings_off)
    return when (rule.status) {
        RuleStatus.NEW -> stringResource(R.string.import_settings_rule_new, onOff)
        RuleStatus.CHANGED -> stringResource(R.string.import_settings_rule_changed, onOff)
        RuleStatus.SAME -> stringResource(R.string.import_settings_rule_same)
    }
}

@Composable
private fun CountsRow(title: Int, counts: MatchCounts) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = {
            Text(
                listOf(
                    R.plurals.import_settings_new to counts.new,
                    R.plurals.import_settings_updated to counts.updated,
                    R.plurals.import_settings_same to counts.same
                ).filter { it.second > 0 }
                    .map { (plural, count) -> pluralStringResource(plural, count, count) }
                    .ifEmpty { listOf(stringResource(R.string.import_settings_none)) }
                    .joinToString(SEPARATOR)
            )
        }
    )
}

private const val SEPARATOR = " · "
