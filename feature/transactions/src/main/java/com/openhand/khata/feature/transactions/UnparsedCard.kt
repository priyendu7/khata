package com.openhand.khata.feature.transactions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.UnparsedSms
import java.time.Instant
import java.time.ZoneId

/**
 * An SMS no rule could read (PRD feature 7): its sender, its text, and what to do with it. The
 * sender is shown rather than a bank, since a business no rule knows has none.
 */
@Composable
internal fun UnparsedCard(
    sms: UnparsedSms,
    left: Int,
    onAddByHand: () -> Unit,
    onMakeParser: () -> Unit,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    val at = Instant.ofEpochMilli(sms.receivedAt).atZone(ZoneId.systemDefault())
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            pluralStringResource(R.plurals.review_left, left, left),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.review_unparsed_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.review_unparsed_body),
            style = MaterialTheme.typography.bodyMedium
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    sms.sender + " · " + dateLabel(at.toLocalDate()) + " · " +
                        timeLabel(at.toLocalTime()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(sms.body, style = MaterialTheme.typography.bodyMedium)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAddByHand) { Text(stringResource(R.string.review_add_by_hand)) }
            OutlinedButton(onClick = onMakeParser) {
                Text(stringResource(R.string.review_make_parser))
            }
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.review_dismiss)) }
            TextButton(onClick = onCopy) { Text(stringResource(R.string.review_copy)) }
        }
    }
}

/** After copying: blank out personal details, then file it on GitHub yourself. */
@Composable
internal fun CopiedDialog(onOpenIssues: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.review_copied_title)) },
        text = { Text(stringResource(R.string.review_copied_body)) },
        confirmButton = {
            TextButton(onClick = {
                onOpenIssues()
                onClose()
            }) { Text(stringResource(R.string.review_open_github)) }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.review_close)) }
        }
    )
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
