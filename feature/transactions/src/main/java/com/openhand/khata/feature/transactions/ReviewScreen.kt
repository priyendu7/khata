package com.openhand.khata.feature.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.ReviewItem
import com.openhand.khata.core.model.SenderId
import com.openhand.khata.core.model.UnparsedSms
import com.openhand.khata.core.model.UnparsedSmsGroup
import com.openhand.khata.core.ui.CategoryBadge
import com.openhand.khata.core.ui.Choice
import com.openhand.khata.core.ui.ChoiceDialog
import com.openhand.khata.core.ui.EmptyState
import com.openhand.khata.core.ui.PickerField
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.SubScreen
import com.openhand.khata.core.ui.TagInput
import com.openhand.khata.core.ui.categoryName
import com.openhand.khata.core.ui.segmentCardColors
import java.time.Instant
import java.time.ZoneId

/**
 * To review (PRD feature 4): name each new payee once, one card at a time; then the bank SMS no
 * rule could read (PRD feature 7), grouped by sender, to add by hand, read with a new parser,
 * ignore or dismiss.
 */
@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    onAddByHand: (amountPaise: Long?, at: Long, unparsedId: Long) -> Unit,
    onMakeParser: (unparsedId: Long) -> Unit,
    onIgnoreLikeThis: (unparsedId: Long) -> Unit,
    viewModel: ReviewViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val unparsed by viewModel.unparsed.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val tagSuggestions by viewModel.tagSuggestions.collectAsStateWithLifecycle()
    ReviewContent(
        onBack = onBack,
        queue = queue,
        categories = categories,
        tagSuggestions = tagSuggestions,
        onTagQueryChange = viewModel::onTagQueryChange,
        onSave = viewModel::save,
        onSkip = viewModel::skip,
        unparsed = unparsed,
        onAddByHand = { onAddByHand(firstAmountPaise(it.body), it.receivedAt, it.id) },
        onMakeParser = { onMakeParser(it.id) },
        onDismiss = viewModel::dismiss,
        onDismissAll = viewModel::dismissAll,
        onCopy = { context.copyText(it.body) },
        onOpenIssues = { context.openIssues() },
        onIgnoreSender = viewModel::ignoreSender,
        onIgnoreLikeThis = { onIgnoreLikeThis(it.id) }
    )
}

@Composable
fun ReviewContent(
    onBack: () -> Unit,
    queue: List<ReviewItem>?,
    categories: List<Category>,
    tagSuggestions: List<String>,
    onTagQueryChange: (String) -> Unit,
    onSave: (ReviewItem, name: String, categoryId: Long?, tags: List<String>) -> Unit,
    onSkip: (ReviewItem) -> Unit,
    unparsed: List<UnparsedSmsGroup> = emptyList(),
    onAddByHand: (UnparsedSms) -> Unit = {},
    onMakeParser: (UnparsedSms) -> Unit = {},
    onDismiss: (UnparsedSms) -> Unit = {},
    onDismissAll: (UnparsedSmsGroup) -> Unit = {},
    onCopy: (UnparsedSms) -> Unit = {},
    onOpenIssues: () -> Unit = {},
    onIgnoreSender: (UnparsedSms) -> Unit = {},
    onIgnoreLikeThis: (UnparsedSms) -> Unit = {}
) {
    var copied by rememberSaveable { mutableStateOf(false) }
    var ignoring by remember { mutableStateOf<UnparsedSms?>(null) }
    SubScreen(title = stringResource(R.string.review_title), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val item = queue?.firstOrNull()
            // The count badges count each SMS, not each sender.
            val unparsedCount = unparsed.sumOf { it.count }
            when {
                queue == null -> Unit
                item == null && unparsed.isNotEmpty() -> UnparsedSmsList(
                    groups = unparsed,
                    left = unparsedCount,
                    onAddByHand = onAddByHand,
                    onMakeParser = onMakeParser,
                    onDismiss = onDismiss,
                    onDismissAll = onDismissAll,
                    onCopy = {
                        onCopy(it)
                        copied = true
                    },
                    onIgnoreSender = { ignoring = it },
                    onIgnoreLikeThis = onIgnoreLikeThis
                )
                item == null -> EmptyState(
                    icon = painterResource(UiR.drawable.ic_ledger),
                    title = stringResource(R.string.review_done_title),
                    body = stringResource(R.string.review_done_body)
                )
                else -> ReviewCard(
                    item = item,
                    left = queue.size + unparsedCount,
                    categories = categories,
                    tagSuggestions = tagSuggestions,
                    onTagQueryChange = onTagQueryChange,
                    onSave = { name, categoryId, tags -> onSave(item, name, categoryId, tags) },
                    onSkip = { onSkip(item) }
                )
            }
        }
    }
    if (copied) CopiedDialog(onOpenIssues = onOpenIssues, onClose = { copied = false })
    ignoring?.let { sms ->
        // Only business senders reach To review, so the sender always has a header.
        val header = SenderId.parse(sms.sender)?.header ?: sms.sender
        IgnoreSenderDialog(
            header = header,
            onIgnore = { onIgnoreSender(sms) },
            onClose = { ignoring = null }
        )
    }
}

@Composable
private fun ReviewCard(
    item: ReviewItem,
    left: Int,
    categories: List<Category>,
    tagSuggestions: List<String>,
    onTagQueryChange: (String) -> Unit,
    onSave: (name: String, categoryId: Long?, tags: List<String>) -> Unit,
    onSkip: () -> Unit
) {
    // A fresh form for each card: keyed by the transaction.
    val key = item.transactionId.toString()
    var name by rememberSaveable(key) {
        mutableStateOf(
            item.payeeName ?: item.payeeIdentifier.orEmpty()
        )
    }
    var categoryId by rememberSaveable(key) { mutableStateOf<Long?>(null) }
    var tags by rememberSaveable(key) { mutableStateOf(emptyList<String>()) }
    var choosingCategory by rememberSaveable(key) { mutableStateOf(false) }
    var showSms by rememberSaveable(key) { mutableStateOf(false) }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            pluralStringResource(R.plurals.review_left, left, left),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TransactionFacts(item, showSms) { showSms = !showSms }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.review_payee_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth()
        )
        val category = categories.firstOrNull { it.id == categoryId }
            ?: categories.firstOrNull { it.isUncategorized }
        PickerField(
            label = stringResource(R.string.field_category),
            value = category?.let { categoryName(it.name, it.seedKey) }
                ?: stringResource(UiR.string.category_uncategorized),
            onClick = { choosingCategory = true },
            leading = category?.let { { CategoryBadge(it.icon, it.color, size = 32.dp) } }
        )
        TagInput(
            tags = tags,
            label = stringResource(R.string.field_tags),
            suggestions = tagSuggestions.filterNot { s ->
                tags.any { it.equals(s, ignoreCase = true) }
            },
            onQueryChange = onTagQueryChange,
            onAdd = { new ->
                if (tags.none { it.equals(new, ignoreCase = true) }) tags = tags + new
            },
            onRemove = { gone -> tags = tags - gone }
        )
        if (item.payeePending > 1) {
            val others = item.payeePending - 1
            Text(
                pluralStringResource(R.plurals.review_fills_others, others, others),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.review_skip)) }
            Button(onClick = { onSave(name, categoryId, tags) }) {
                Text(stringResource(R.string.review_save))
            }
        }
    }
    if (choosingCategory) {
        ChoiceDialog(
            title = stringResource(R.string.field_category),
            choices = categories.sortedBy { it.isUncategorized }.map {
                Choice<Long?>(
                    value = if (it.isUncategorized) null else it.id,
                    label = categoryName(it.name, it.seedKey),
                    leading = { CategoryBadge(it.icon, it.color, size = 32.dp) }
                )
            },
            selected = categoryId,
            onSelect = {
                categoryId = it
                choosingCategory = false
            },
            onDismiss = { choosingCategory = false }
        )
    }
}

/** Amount, when, which account, and what the SMS called the payee; the SMS itself on request. */
@Composable
private fun TransactionFacts(item: ReviewItem, showSms: Boolean, onToggleSms: () -> Unit) {
    val at = Instant.ofEpochMilli(item.timestamp).atZone(ZoneId.systemDefault())
    Card(Modifier.fillMaxWidth(), colors = segmentCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                signedAmount(item.direction, item.amountPaise),
                style = MaterialTheme.typography.headlineMedium,
                color = amountColor(item.direction)
            )
            Text(
                listOfNotNull(
                    stringResource(item.direction.label()),
                    dateLabel(at.toLocalDate()) + " · " + timeLabel(at.toLocalTime()),
                    item.accountName
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium
            )
            item.payeeIdentifier?.let {
                Text(
                    stringResource(R.string.review_from_sms, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item.rawSms?.let { sms ->
                TextButton(onClick = onToggleSms, contentPadding = PaddingValues(0.dp)) {
                    Text(
                        stringResource(
                            if (showSms) R.string.review_hide_sms else R.string.review_show_sms
                        )
                    )
                }
                if (showSms) Text(sms, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** "3 transactions to review", above the list while any wait (PRD feature 4). */
@Composable
internal fun ReviewBanner(count: Int, onReview: () -> Unit) {
    Card(
        onClick = onReview,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            pluralStringResource(R.plurals.review_banner, count, count),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(16.dp)
        )
    }
}
