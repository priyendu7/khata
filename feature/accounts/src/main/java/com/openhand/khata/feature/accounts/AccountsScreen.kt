package com.openhand.khata.feature.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.ui.EmptyState
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.SubScreen

@Composable
fun AccountsScreen(onBack: () -> Unit, viewModel: AccountsViewModel = hiltViewModel()) {
    val accounts by viewModel.list.collectAsStateWithLifecycle()
    // The account being edited (id 0 = new), or null.
    var editing by rememberSaveable(stateSaver = AccountSaver) { mutableStateOf<Account?>(null) }
    var deleting by rememberSaveable(stateSaver = DeleteSaver) {
        mutableStateOf<Pair<Account, Int>?>(null)
    }

    SubScreen(
        title = stringResource(R.string.accounts_title),
        onBack = onBack,
        onAdd = { editing = Account(name = "", type = AccountType.BANK) },
        addLabel = stringResource(R.string.accounts_add)
    ) { padding ->
        val list = accounts
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(
                icon = painterResource(UiR.drawable.ic_ledger),
                title = stringResource(R.string.accounts_empty_title),
                body = stringResource(R.string.accounts_empty_body),
                modifier = Modifier.padding(padding)
            )
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(list, key = { it.id }) { account ->
                    ListItem(
                        headlineContent = { Text(account.name) },
                        supportingContent = { Text(accountSummary(account)) },
                        modifier = Modifier.clickable { editing = account }
                    )
                }
            }
        }
    }

    editing?.let { account ->
        AccountEditor(
            account = account,
            onSave = {
                viewModel.save(it)
                editing = null
            },
            onDelete = {
                viewModel.countTransactions(account.id) { count ->
                    editing = null
                    deleting = account to count
                }
            },
            onDismiss = { editing = null }
        )
    }
    deleting?.let { (account, count) ->
        DeleteAccountDialog(
            account = account,
            transactionCount = count,
            otherAccounts = accounts.orEmpty().filter { it.id != account.id },
            onDelete = { moveTo ->
                viewModel.delete(account.id, moveTo)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

/** "Credit card · HDFC · ••1234" */
@Composable
private fun accountSummary(account: Account): String = listOfNotNull(
    stringResource(account.type.label()),
    account.bank,
    account.last4?.let { stringResource(R.string.accounts_last4_display, it) }
).joinToString(" · ")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountEditor(
    account: Account,
    onSave: (Account) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(account.name) }
    var type by rememberSaveable { mutableStateOf(account.type) }
    var bank by rememberSaveable { mutableStateOf(account.bank.orEmpty()) }
    var last4 by rememberSaveable { mutableStateOf(account.last4.orEmpty()) }
    val valid = name.isNotBlank() && Account.isValidLast4(last4)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (account.id ==
                        0L
                    ) {
                        R.string.accounts_add
                    } else {
                        R.string.accounts_edit
                    }
                )
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.accounts_name)) },
                    placeholder = { Text(stringResource(R.string.accounts_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                // Wraps onto a second line on narrow screens rather than squeezing the labels.
                FlowRow(
                    Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AccountType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(stringResource(option.shortLabel())) }
                        )
                    }
                }
                OutlinedTextField(
                    value = bank,
                    onValueChange = { bank = it },
                    label = { Text(stringResource(R.string.accounts_bank)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = last4,
                    onValueChange = { new ->
                        if (new.length <= Account.LAST_DIGITS &&
                            new.all(Char::isDigit)
                        ) {
                            last4 = new
                        }
                    },
                    label = { Text(stringResource(R.string.accounts_last4)) },
                    supportingText = { Text(stringResource(R.string.accounts_last4_help)) },
                    isError = !Account.isValidLast4(last4),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(account.copy(name = name, type = type, bank = bank, last4 = last4))
                }
            ) { Text(stringResource(UiR.string.save)) }
        },
        dismissButton = {
            Row {
                if (account.id !=
                    0L
                ) {
                    TextButton(onClick = onDelete) { Text(stringResource(UiR.string.delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.cancel)) }
            }
        }
    )
}

/** Deleting never loses transactions: they move to another account or to "no account". */
@Composable
private fun DeleteAccountDialog(
    account: Account,
    transactionCount: Int,
    otherAccounts: List<Account>,
    onDelete: (moveTo: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var moveTo by rememberSaveable { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accounts_delete_title, account.name)) },
        text = {
            if (transactionCount == 0) {
                Text(stringResource(R.string.accounts_delete_unused))
            } else {
                Column {
                    Text(
                        pluralStringResource(
                            R.plurals.accounts_delete_move,
                            transactionCount,
                            transactionCount
                        )
                    )
                    MoveOption(stringResource(R.string.accounts_no_account), moveTo == null) {
                        moveTo =
                            null
                    }
                    otherAccounts.forEach { other ->
                        MoveOption(other.name, moveTo == other.id) {
                            moveTo =
                                other.id
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDelete(moveTo) }) { Text(stringResource(UiR.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.cancel)) }
        }
    )
}

@Composable
private fun MoveOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label)
    }
}

private fun AccountType.label() = when (this) {
    AccountType.BANK -> R.string.account_type_bank
    AccountType.CREDIT_CARD -> R.string.account_type_credit_card
    AccountType.DEBIT_CARD -> R.string.account_type_debit_card
    AccountType.WALLET -> R.string.account_type_wallet
}

private fun AccountType.shortLabel() = when (this) {
    AccountType.BANK -> R.string.account_type_bank_short
    AccountType.CREDIT_CARD -> R.string.account_type_credit_card_short
    AccountType.DEBIT_CARD -> R.string.account_type_debit_card_short
    AccountType.WALLET -> R.string.account_type_wallet_short
}
