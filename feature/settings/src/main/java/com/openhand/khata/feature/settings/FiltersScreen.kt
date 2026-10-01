package com.openhand.khata.feature.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.ui.SubScreen
import com.openhand.khata.sms.parser.SmsFilters

/**
 * Settings > SMS import > Filters (PRD feature 7): every filter can be switched off, and all are
 * on by default. A change applies to the next SMS.
 */
@Composable
fun FiltersScreen(onBack: () -> Unit, viewModel: FiltersViewModel = hiltViewModel()) {
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    FiltersContent(
        onBack = onBack,
        filters = filters,
        onChange = viewModel::set,
        shown = viewModel.shown
    )
}

@Composable
fun FiltersContent(
    onBack: () -> Unit,
    filters: SmsFilters,
    onChange: (FilterSwitch, Boolean) -> Unit,
    shown: FilterSwitch? = null
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
