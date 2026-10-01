package com.openhand.khata.feature.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.model.IgnoreKind
import com.openhand.khata.core.model.SmsIgnoreRule
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.SubScreen
import com.openhand.khata.sms.parser.SmsFilters

/**
 * Settings > SMS import > Filters (PRD feature 7): every filter can be switched off, and all are
 * on by default. A change applies to the next SMS.
 */
@Composable
fun FiltersScreen(onBack: () -> Unit, viewModel: FiltersViewModel = hiltViewModel()) {
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    FiltersContent(
        onBack = onBack,
        filters = filters,
        onChange = viewModel::set,
        shown = viewModel.shown,
        rules = rules,
        onRuleEnabled = viewModel::setRuleEnabled,
        onDeleteRule = viewModel::deleteRule
    )
}

@Composable
fun FiltersContent(
    onBack: () -> Unit,
    filters: SmsFilters,
    onChange: (FilterSwitch, Boolean) -> Unit,
    shown: FilterSwitch? = null,
    rules: List<SmsIgnoreRule> = emptyList(),
    onRuleEnabled: (SmsIgnoreRule, Boolean) -> Unit = { _, _ -> },
    onDeleteRule: (SmsIgnoreRule) -> Unit = {}
) {
    SubScreen(title = stringResource(R.string.filters_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Section(R.string.filters_before_reading)
            // Not a switch: people's messages are never read.
            ListItem(
                headlineContent = { Text(stringResource(R.string.filter_phone_numbers)) },
                supportingContent = { Text(stringResource(R.string.filter_phone_numbers_note)) }
            )
            Switches(FilterSwitch.entries.filter { it.beforeReading }, filters, onChange, shown)
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Section(R.string.filters_no_parser)
            Switches(FilterSwitch.entries.filterNot { it.beforeReading }, filters, onChange, shown)
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Section(R.string.filters_ignore_rules)
            if (rules.isEmpty()) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.filters_no_ignore_rules)) }
                )
            }
            rules.forEach { IgnoreRuleRow(it, onRuleEnabled, onDeleteRule) }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Text(
                stringResource(R.string.filters_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun Section(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Switches(
    switches: List<FilterSwitch>,
    filters: SmsFilters,
    onChange: (FilterSwitch, Boolean) -> Unit,
    shown: FilterSwitch?
) {
    switches.forEach { switch ->
        val on = switch.isOn(filters)
        val requester = remember { BringIntoViewRequester() }
        if (switch == shown) LaunchedEffect(Unit) { requester.bringIntoView() }
        ListItem(
            headlineContent = { Text(stringResource(switch.title)) },
            supportingContent = { Text(stringResource(switch.summary)) },
            trailingContent = { Switch(checked = on, onCheckedChange = null) },
            // The one Test a message named.
            colors = if (switch == shown) {
                ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            } else {
                ListItemDefaults.colors()
            },
            modifier = Modifier
                .bringIntoViewRequester(requester)
                .toggleable(value = on, role = Role.Switch) { onChange(switch, it) }
        )
    }
}

@Composable
private fun IgnoreRuleRow(
    rule: SmsIgnoreRule,
    onEnabled: (SmsIgnoreRule, Boolean) -> Unit,
    onDelete: (SmsIgnoreRule) -> Unit
) {
    val title = when (rule.kind) {
        IgnoreKind.SENDER -> stringResource(R.string.filters_ignored_sender, rule.header)
        IgnoreKind.TEMPLATE -> stringResource(R.string.filters_ignored_like, rule.header)
    }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(rule.sample, maxLines = SAMPLE_LINES, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDelete(rule) }) {
                    Icon(
                        painterResource(UiR.drawable.ic_delete),
                        contentDescription = stringResource(UiR.string.delete)
                    )
                }
                Switch(checked = rule.enabled, onCheckedChange = null)
            }
        },
        modifier = Modifier.toggleable(value = rule.enabled, role = Role.Switch) {
            onEnabled(rule, it)
        }
    )
}

private const val SAMPLE_LINES = 2
