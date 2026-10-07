package com.openhand.khata.feature.transactions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.UnparsedSms
import com.openhand.khata.core.model.UnparsedSmsGroup
import java.time.Instant
import java.time.ZoneId

/**
 * The SMS no rule could read (PRD feature 7), one card per sender header, the newest first. A
 * group of several shows its newest SMS and acts on all of them at once; expanded, each SMS has
 * its own actions. The sender is shown rather than a bank, since a business no rule knows has none.
 */
@Composable
internal fun UnparsedSmsList(
    groups: List<UnparsedSmsGroup>,
    left: Int,
    onAddByHand: (UnparsedSms) -> Unit,
    onMakeParser: (UnparsedSms) -> Unit,
    onDismiss: (UnparsedSms) -> Unit,
    onDismissAll: (UnparsedSmsGroup) -> Unit,
    onCopy: (UnparsedSms) -> Unit,
    onIgnoreSender: (UnparsedSms) -> Unit,
    onIgnoreLikeThis: (UnparsedSms) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var dismissing by remember { mutableStateOf<UnparsedSmsGroup?>(null) }
    val single = groups.sumOf { it.count } == 1
    val smsActions: @Composable (UnparsedSms, Boolean) -> Unit = { sms, withSender ->
        SmsActions(
            onAddByHand = { onAddByHand(sms) },
            onMakeParser = { onMakeParser(sms) },
            onDismiss = { onDismiss(sms) },
            onCopy = { onCopy(sms) },
            onIgnoreSender = if (withSender) ({ onIgnoreSender(sms) }) else null,
            onIgnoreLikeThis = { onIgnoreLikeThis(sms) }
        )
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "intro") { UnparsedIntro(left, single) }
        groups.forEach { group ->
            val open = group.header in expanded
            item(key = "group:" + group.header) {
                if (group.count == 1) {
                    SmsCard(group.newest) { smsActions(group.newest, true) }
                } else {
                    GroupCard(
                        group = group,
                        expanded = open,
                        onToggle = {
                            expanded =
                                if (open) expanded - group.header else expanded + group.header
                        },
                        onMakeParser = { onMakeParser(group.newest) },
                        onIgnoreSender = { onIgnoreSender(group.newest) },
                        onDismissAll = { dismissing = group }
                    )
                }
            }
            if (open && group.count > 1) {
                items(group.messages, key = { "sms:" + it.id }) { sms ->
                    SmsCard(sms, Modifier.padding(start = 16.dp)) { smsActions(sms, false) }
                }
            }
        }
    }
    dismissing?.let { group ->
        DismissAllDialog(
            header = group.header,
            count = group.count,
            onDismissAll = { onDismissAll(group) },
            onClose = { dismissing = null }
        )
    }
}

@Composable
private fun UnparsedIntro(left: Int, single: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            pluralStringResource(R.plurals.review_left, left, left),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(
                if (single) R.string.review_unparsed_title else R.string.review_unparsed_title_many
            ),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(
                if (single) R.string.review_unparsed_body else R.string.review_unparsed_body_many
            ),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** "HDFCBK · 34 messages", the newest one, and what to do with them all. */
@Composable
private fun GroupCard(
    group: UnparsedSmsGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    onMakeParser: () -> Unit,
    onIgnoreSender: () -> Unit,
    onDismissAll: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                pluralStringResource(
                    R.plurals.review_group_title,
                    group.count,
                    group.header,
                    group.count
                ),
                style = MaterialTheme.typography.titleSmall
            )
            // Expanded, the newest is the first in the list below.
            if (!expanded) SmsText(group.newest)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onMakeParser) { Text(stringResource(R.string.review_make_parser)) }
                OutlinedButton(onClick = onIgnoreSender) {
                    Text(stringResource(R.string.review_ignore_sender))
                }
                OutlinedButton(onClick = onDismissAll) {
                    Text(stringResource(R.string.review_dismiss_all))
                }
            }
            TextButton(onClick = onToggle) {
                Text(
                    if (expanded) {
                        stringResource(R.string.review_group_hide)
                    } else {
                        pluralStringResource(R.plurals.review_group_show, group.count, group.count)
                    }
                )
            }
        }
    }
}

@Composable
private fun SmsCard(
    sms: UnparsedSms,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SmsText(sms)
            actions()
        }
    }
}

/** The sender, when it came, and its text. */
@Composable
private fun SmsText(sms: UnparsedSms) {
    val at = Instant.ofEpochMilli(sms.receivedAt).atZone(ZoneId.systemDefault())
    Text(
        sms.sender + " · " + dateLabel(at.toLocalDate()) + " · " + timeLabel(at.toLocalTime()),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(sms.body, style = MaterialTheme.typography.bodyMedium)
}

/** What to do with one SMS. Inside a group, ignoring the sender is the group's action. */
@Composable
private fun SmsActions(
    onAddByHand: () -> Unit,
    onMakeParser: () -> Unit,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onIgnoreSender: (() -> Unit)?,
    onIgnoreLikeThis: () -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onAddByHand) { Text(stringResource(R.string.review_add_by_hand)) }
        OutlinedButton(onClick = onMakeParser) {
            Text(stringResource(R.string.review_make_parser))
        }
        OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.review_dismiss)) }
        OutlinedButton(onClick = onIgnoreLikeThis) {
            Text(stringResource(R.string.review_ignore_like_this))
        }
        onIgnoreSender?.let {
            OutlinedButton(onClick = it) { Text(stringResource(R.string.review_ignore_sender)) }
        }
        TextButton(onClick = onCopy) { Text(stringResource(R.string.review_copy)) }
    }
}

/** The first amount in an SMS (`Rs.2,000`, `INR 18.00`, `₹99`), to start the Add screen with. */
internal fun firstAmountPaise(body: String): Long? =
    AMOUNT.find(body)?.groupValues?.get(1)?.let(Money::parsePaise)?.takeIf { it > 0 }

private val AMOUNT =
    Regex("""(?:\b(?:rs\.?|inr)|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)

internal fun Context.copyText(text: String) {
    val clipboard = getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("SMS", text))
}

/**
 * Opens a blank new GitHub issue in the browser. Nothing from the SMS goes into the link: the
 * user pastes it themselves, after blanking out personal details. Khata sends nothing.
 */
internal fun Context.openIssues() {
    startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(NEW_ISSUE_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private const val NEW_ISSUE_URL = "https://github.com/priyendu7/khata/issues/new"
