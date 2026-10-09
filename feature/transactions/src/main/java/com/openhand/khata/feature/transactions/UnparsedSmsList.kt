package com.openhand.khata.feature.transactions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.UnparsedSms
import com.openhand.khata.core.model.UnparsedSmsGroup
import com.openhand.khata.core.ui.segment
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
    // One segmented list: each sender's card, followed by its messages while it's open.
    val rows = groups.flatMap { group ->
        val open = group.count > 1 && group.header in expanded
        listOf<Pair<UnparsedSmsGroup, UnparsedSms?>>(group to null) +
            if (open) group.messages.map { group to it } else emptyList()
    }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item(key = "intro") { UnparsedIntro(left, single) }
        itemsIndexed(
            rows,
            key = { _, (group, sms) -> sms?.let { "sms:" + it.id } ?: ("group:" + group.header) }
        ) { index, (group, sms) ->
            val open = group.header in expanded
            val segment = Modifier.segment(index, rows.size)
            when {
                sms != null || group.count == 1 -> {
                    val one = sms ?: group.newest
                    // Alone, it has every action; in an open group, the group has some.
                    val alone = sms == null
                    SmsCard(
                        sms = one,
                        onAddByHand = { onAddByHand(one) },
                        onMakeParser = if (alone) ({ onMakeParser(one) }) else null,
                        onDismiss = { onDismiss(one) },
                        onCopy = { onCopy(one) },
                        onIgnoreSender = if (alone) ({ onIgnoreSender(one) }) else null,
                        onIgnoreLikeThis = { onIgnoreLikeThis(one) },
                        modifier = segment
                    )
                }
                else -> {
                    GroupCard(
                        group = group,
                        expanded = open,
                        onToggle = {
                            expanded =
                                if (open) expanded - group.header else expanded + group.header
                        },
                        onMakeParser = { onMakeParser(group.newest) },
                        onIgnoreSender = { onIgnoreSender(group.newest) },
                        onDismissAll = { dismissing = group },
                        modifier = segment
                    )
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
    Column(Modifier.padding(bottom = 16.dp)) {
        Text(
            pluralStringResource(R.plurals.review_left, left, left),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(
                if (single) R.string.review_unparsed_title else R.string.review_unparsed_title_many
            ),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
        )
        // Why, then what the two less obvious buttons are for; Add by hand speaks for itself.
        listOf(
            if (single) R.string.review_unparsed_body else R.string.review_unparsed_body_many,
            R.string.review_unparsed_how_parser,
            R.string.review_unparsed_how_dismiss
        ).forEachIndexed { index, text ->
            Text(
                (if (index == 0) "" else "• ") + stringResource(text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

/**
 * "HDFCBK · 34 messages", the newest one, and what to do with them all. Every card here has the
 * same parts: a top line with the menu at its end, the amount and the SMS, then the buttons.
 */
@Composable
private fun GroupCard(
    group: UnparsedSmsGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    onMakeParser: () -> Unit,
    onIgnoreSender: () -> Unit,
    onDismissAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    CardColumn(modifier) {
        TopLine(
            pluralStringResource(
                R.plurals.review_group_title,
                group.count,
                group.header,
                group.count
            ),
            menu = listOf(R.string.review_ignore_sender to onIgnoreSender)
        )
        // Expanded, the newest is the first in the list below.
        if (!expanded) SmsText(group.newest)
        TextButton(onClick = onToggle, contentPadding = PaddingValues(0.dp)) {
            Text(
                if (expanded) {
                    stringResource(R.string.review_group_hide)
                } else {
                    pluralStringResource(R.plurals.review_group_show, group.count, group.count)
                }
            )
        }
        ActionRow(
            primary = R.string.review_make_parser to onMakeParser,
            others = listOf(R.string.review_dismiss_all to onDismissAll)
        )
    }
}

/**
 * One SMS. Alone, Make a parser is its main action; inside an open group the group's card has it,
 * and ignoring the sender.
 */
@Composable
private fun SmsCard(
    sms: UnparsedSms,
    onAddByHand: () -> Unit,
    onMakeParser: (() -> Unit)?,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onIgnoreSender: (() -> Unit)?,
    onIgnoreLikeThis: () -> Unit,
    modifier: Modifier = Modifier
) {
    val at = Instant.ofEpochMilli(sms.receivedAt).atZone(ZoneId.systemDefault())
    CardColumn(modifier) {
        TopLine(
            sms.sender + " · " + dateLabel(at.toLocalDate()) + " · " + timeLabel(at.toLocalTime()),
            menu = listOfNotNull(
                R.string.review_ignore_like_this to onIgnoreLikeThis,
                onIgnoreSender?.let { R.string.review_ignore_sender to it },
                R.string.review_copy to onCopy
            )
        )
        SmsText(sms)
        Spacer(Modifier.height(8.dp))
        ActionRow(
            primary = onMakeParser?.let { R.string.review_make_parser to it },
            others = listOf(
                R.string.review_add_by_hand to onAddByHand,
                R.string.review_dismiss to onDismiss
            )
        )
    }
}

@Composable
private fun CardColumn(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}

/** Who sent it (and when, or how many), with the less common actions in a menu at its end. */
@Composable
private fun TopLine(text: String, menu: List<Pair<Int, () -> Unit>>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        MoreMenu(menu)
    }
}

/** The amount, as large as on the payee cards, then the SMS itself. */
@Composable
private fun SmsText(sms: UnparsedSms) {
    // The end padding makes up for the card's, which is narrow for the menu button.
    Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        firstAmountPaise(sms.body)?.let {
            Text(Money.format(it), style = MaterialTheme.typography.headlineMedium)
        }
        Text(sms.body, style = MaterialTheme.typography.bodyMedium)
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
