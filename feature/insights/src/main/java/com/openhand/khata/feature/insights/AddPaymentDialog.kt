package com.openhand.khata.feature.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.CardStatement
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.UnlinkedPayment
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.StatusChip
import com.openhand.khata.core.ui.Tone

/**
 * "Add a payment" on a card's page (#166): card bills paid from other accounts that Khata
 * couldn't match to a card, those of the statement's total or minimum first. Picking one links
 * it ([onLink]); [onRecord] records a payment that had no SMS at all.
 */
@Composable
internal fun AddPaymentDialog(
    statement: CardStatement,
    unlinked: List<UnlinkedPayment>,
    onLink: (Long) -> Unit,
    onRecord: () -> Unit,
    onDismiss: () -> Unit
) {
    val shown = UnlinkedPayment.forStatement(unlinked, statement)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.card_add_payment)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    Text(
                        stringResource(
                            if (shown.isEmpty()) {
                                R.string.card_add_payment_none
                            } else {
                                R.string.card_add_payment_body
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(shown, key = { it.transactionId }) { payment ->
                    UnlinkedRow(payment, matches = matches(payment, statement)) {
                        onLink(payment.transactionId)
                    }
                }
                item {
                    TextButton(onClick = onRecord) {
                        Text(stringResource(R.string.card_record_payment))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.cancel)) }
        }
    )
}

private fun matches(payment: UnlinkedPayment, statement: CardStatement) =
    payment.amountPaise == statement.totalPaise || payment.amountPaise == statement.minDuePaise

/** A payment's amount and day, who it went to from which account, and whether it fits. */
@Composable
private fun UnlinkedRow(payment: UnlinkedPayment, matches: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(
                    R.string.card_payment,
                    Money.format(payment.amountPaise),
                    longDate(payment.timestamp.toLocalDate())
                ),
                style = MaterialTheme.typography.bodyLarge
            )
            val from = listOfNotNull(payment.payeeName, payment.accountName).joinToString(" · ")
            if (from.isNotEmpty()) {
                Text(
                    from,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (matches) StatusChip(stringResource(R.string.card_matches_bill), Tone.POSITIVE)
    }
}
