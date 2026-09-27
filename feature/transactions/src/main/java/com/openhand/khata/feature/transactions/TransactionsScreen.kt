package com.openhand.khata.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.TransactionFilter
import com.openhand.khata.core.model.TransactionListItem
import com.openhand.khata.core.ui.CategoryBadge
import com.openhand.khata.core.ui.Choice
import com.openhand.khata.core.ui.ChoiceDialog
import com.openhand.khata.core.ui.DateRangeDialog
import com.openhand.khata.core.ui.EmptyState
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.ScreenTitle
import com.openhand.khata.core.ui.SubScreen
import com.openhand.khata.core.ui.categoryName
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The "+" button shown on Home and Transactions. */
@Composable
fun AddTransactionButton(onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick) {
        Icon(
            painterResource(UiR.drawable.ic_add),
            contentDescription = stringResource(R.string.transactions_add)
        )
    }
}

/**
 * Every transaction, newest first and grouped by day, with search and filters. Opened from another
 * screen (with [onBack]), it has a back arrow and starts with the filters it was opened with.
 */
@Composable
fun TransactionsScreen(
    onOpen: (transactionId: Long) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val title = stringResource(UiR.string.nav_transactions)
    if (onBack == null) {
        Column(modifier.fillMaxSize()) {
            ScreenTitle(title)
            TransactionList(onOpen, viewModel)
        }
    } else {
        SubScreen(title, onBack, modifier) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) { TransactionList(onOpen, viewModel) }
        }
    }
}

@Composable
private fun TransactionList(onOpen: (Long) -> Unit, viewModel: TransactionsViewModel) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val state by viewModel.days.collectAsStateWithLifecycle()

    Column {
        SearchField(filter.query, viewModel::setQuery)
        FilterRow(filter, viewModel)
        val days = state?.days
        when {
            days == null -> Unit
            days.isEmpty() && state?.filter?.isFiltered == true -> EmptyState(
                icon = painterResource(UiR.drawable.ic_search),
                title = stringResource(R.string.transactions_no_match_title),
                body = stringResource(R.string.transactions_no_match_body)
            )
            days.isEmpty() -> EmptyState(
                icon = painterResource(UiR.drawable.ic_ledger),
                title = stringResource(R.string.transactions_empty_title),
                body = stringResource(R.string.transactions_empty_body)
            )
            else -> DayList(days, onOpen)
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.transactions_search)) },
        leadingIcon = { Icon(painterResource(UiR.drawable.ic_search), contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(
                        painterResource(UiR.drawable.ic_close),
                        contentDescription = stringResource(R.string.transactions_clear_search)
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    )
}

private enum class FilterDialog { CATEGORY, TAG, ACCOUNT, DATES }

@Composable
private fun FilterRow(filter: TransactionFilter, viewModel: TransactionsViewModel) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<FilterDialog?>(null) }
    val close = { dialog = null }

    val category = categories.firstOrNull { it.id == filter.categoryId }
    val zone = ZoneId.systemDefault()
    val first = filter.from?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    val last = filter.until?.let {
        Instant.ofEpochMilli(it).atZone(zone).toLocalDate().minusDays(1)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        FilterButton(
            label = category?.let { categoryName(it.name, it.seedKey) }
                ?: stringResource(R.string.field_category),
            selected = category != null
        ) { dialog = FilterDialog.CATEGORY }
        FilterButton(
            label = tags.firstOrNull { it.id == filter.tagId }?.name
                ?: stringResource(R.string.field_tag),
            selected = filter.tagId != null
        ) { dialog = FilterDialog.TAG }
        FilterButton(
            label = accounts.firstOrNull { it.id == filter.accountId }?.name
                ?: stringResource(R.string.field_account),
            selected = filter.accountId != null
        ) { dialog = FilterDialog.ACCOUNT }
        FilterButton(
            label = if (first != null && last != null) {
                dateRangeLabel(first, last)
            } else {
                stringResource(R.string.filter_dates)
            },
            selected = first != null
        ) { dialog = FilterDialog.DATES }
        val filtered = filter.copy(query = "").isFiltered
        if (filtered) {
            TextButton(onClick = viewModel::clearFilters) {
                Text(stringResource(R.string.filter_clear))
            }
        }
    }

    when (dialog) {
        null -> Unit
        FilterDialog.CATEGORY -> ChoiceDialog(
            title = stringResource(R.string.field_category),
            choices = listOf(Choice<Long?>(null, stringResource(R.string.filter_any_category))) +
                categories.sortedBy { it.isUncategorized }.map {
                    Choice(
                        value = it.id,
                        label = categoryName(it.name, it.seedKey),
                        leading = { CategoryBadge(it.icon, it.color, size = 32.dp) }
                    )
                },
            selected = filter.categoryId,
            onSelect = {
                viewModel.setCategory(it)
                close()
            },
            onDismiss = close
        )
        FilterDialog.TAG -> ChoiceDialog(
            title = stringResource(R.string.field_tag),
            choices = listOf(Choice<Long?>(null, stringResource(R.string.filter_any_tag))) +
                tags.map { Choice(it.id, it.name) },
            selected = filter.tagId,
            onSelect = {
                viewModel.setTag(it)
                close()
            },
            onDismiss = close
        )
        FilterDialog.ACCOUNT -> ChoiceDialog(
            title = stringResource(R.string.field_account),
            choices = listOf(Choice<Long?>(null, stringResource(R.string.filter_any_account))) +
                accounts.map { Choice(it.id, it.name) },
            selected = filter.accountId,
            onSelect = {
                viewModel.setAccount(it)
                close()
            },
            onDismiss = close
        )
        FilterDialog.DATES -> DateRangeDialog(
            start = first,
            end = last,
            onPick = { from, to ->
                viewModel.setDates(from, to)
                close()
            },
            onDismiss = close
        )
    }
}

@Composable
private fun dateRangeLabel(first: LocalDate, last: LocalDate): String =
    if (first == last) dateLabel(first) else dateLabel(first) + " – " + dateLabel(last)

@Composable
private fun FilterButton(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon = {
            Icon(painterResource(UiR.drawable.ic_arrow_drop_down), contentDescription = null)
        }
    )
}

@Composable
private fun DayList(days: List<DaySection>, onOpen: (Long) -> Unit) {
    // Room for the "+" button so it never covers the last row.
    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp), modifier = Modifier.fillMaxSize()) {
        days.forEach { day ->
            stickyHeader(key = "day-${day.date}", contentType = "day") { DayHeader(day) }
            items(day.items, key = { it.id }, contentType = { "transaction" }) { item ->
                TransactionRow(item) { onOpen(item.id) }
            }
        }
    }
}

@Composable
private fun DayHeader(day: DaySection) {
    val totals = listOfNotNull(
        day.totals.spentPaise.takeIf { it > 0 }?.let {
            stringResource(R.string.day_spent, Money.format(it))
        },
        day.totals.incomePaise.takeIf { it > 0 }?.let {
            stringResource(R.string.day_received, Money.format(it))
        }
    ).joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            dayLabel(day.date),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            totals,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TransactionRow(item: TransactionListItem, onClick: () -> Unit) {
    val category = categoryName(item.category.name, item.category.seedKey)
    val details = listOfNotNull(
        stringResource(item.direction.label()).takeIf { item.direction != Direction.DEBIT },
        category.takeIf { item.payeeName != null },
        item.accountName,
        item.note,
        item.tags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" }
    ).joinToString(" · ")
    val time = Instant.ofEpochMilli(item.timestamp).atZone(ZoneId.systemDefault()).toLocalTime()
    ListItem(
        leadingContent = { CategoryBadge(item.category.icon, item.category.color) },
        headlineContent = {
            Text(item.payeeName ?: category, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = if (details.isEmpty()) {
            null
        } else {
            { Text(details, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    signedAmount(item.direction, item.amountPaise),
                    style = MaterialTheme.typography.titleMedium,
                    color = amountColor(item.direction)
                )
                Text(timeLabel(time), style = MaterialTheme.typography.labelSmall)
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
