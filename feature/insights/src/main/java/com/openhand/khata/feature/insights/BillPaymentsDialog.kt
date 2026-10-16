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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.TransferMove
import com.openhand.khata.core.ui.Choice
import com.openhand.khata.core.ui.ChoiceDialog
import com.openhand.khata.core.ui.R as UiR

/**
 * The payments behind a line of "Card bills paid" (#166), newest first; tapping one opens it.
 * For a card that isn't known, each has "Choose card" to link it to one of [creditCards].
 */
@Composable
internal fun BillPaymentsDialog(
    title: String,
    payments: List<TransferMove>,
    onOpen: (Long) -> Unit,
    onDismiss: () -> Unit,
    creditCards: List<Account>? = null,
    onLink: (transactionId: Long, cardId: Long) -> Unit = { _, _ -> }
) {
    var choosingFor by rememberSaveable { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(payments, key = { it.transactionId }) { move ->
                    PaymentLine(
                        move,
                        onOpen = { onOpen(move.transactionId) },
                        onChooseCard = creditCards?.let { { choosingFor = move.transactionId } }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.ok)) }
        }
    )
    val payment = choosingFor
    if (payment != null && creditCards != null) {
        val close = { choosingFor = null }
        if (creditCards.isEmpty()) {
            AlertDialog(
                onDismissRequest = close,
                text = { Text(stringResource(R.string.insights_no_credit_cards)) },
                confirmButton = {
                    TextButton(onClick = close) { Text(stringResource(UiR.string.ok)) }
                }
            )
        } else {
            ChoiceDialog<Long?>(
                title = stringResource(R.string.insights_choose_card),
                choices = creditCards.map { Choice(it.id, cardLabel(it)) },
                selected = null,
                onSelect = { card ->
                    card?.let { onLink(payment, it) }
                    close()
                },
                onDismiss = close
            )
        }
    }
}

@Composable
private fun PaymentLine(move: TransferMove, onOpen: () -> Unit, onChooseCard: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 8.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(
                    R.string.card_payment,
                    Money.format(move.amountPaise),
                    longDate(move.timestamp.toLocalDate())
                ),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                move.from.account?.let { accountLabel(it) } ?: move.from.payeeName.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        onChooseCard?.let {
            TextButton(onClick = it) { Text(stringResource(R.string.insights_choose_card)) }
        }
    }
}
