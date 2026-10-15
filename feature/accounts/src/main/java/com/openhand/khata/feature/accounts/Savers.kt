package com.openhand.khata.feature.accounts

import androidx.compose.runtime.saveable.Saver
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountType

// Keep an open editor or delete dialog across rotation and process death.

private fun Account.toList(): List<Any?> =
    listOf(id, name, type.name, bank, last4, billingDay, linkedAccountId)

/** Reads [toList]'s values back, in the same order. */
private fun List<Any?>.toAccount(): Account {
    val values = iterator()
    return Account(
        id = values.next() as Long,
        name = values.next() as String,
        type = AccountType.valueOf(values.next() as String),
        bank = values.next() as String?,
        last4 = values.next() as String?,
        billingDay = values.next() as Int?,
        linkedAccountId = values.next() as Long?
    )
}

internal val AccountSaver = Saver<Account?, List<Any?>>(
    save = { it?.toList() },
    restore = { it.toAccount() }
)

internal val DeleteSaver = Saver<Pair<Account, Int>?, List<Any?>>(
    save = { pair -> pair?.let { (a, count) -> a.toList() + count } },
    restore = { it.toAccount() to it[ACCOUNT_FIELDS] as Int }
)

/** How many of a saved list's items are the account's; the delete dialog adds its count. */
private const val ACCOUNT_FIELDS = 7
