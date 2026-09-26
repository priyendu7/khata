package com.openhand.khata.feature.accounts

import androidx.compose.runtime.saveable.Saver
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountType

// Keep an open editor or delete dialog across rotation and process death.

internal val AccountSaver = Saver<Account?, List<Any?>>(
    save = { it?.let { a -> listOf(a.id, a.name, a.type.name, a.bank, a.last4) } },
    restore = {
        Account(
            it[0] as Long,
            it[1] as String,
            AccountType.valueOf(it[2] as String),
            it[3] as String?,
            it[4] as String?
        )
    }
)

internal val DeleteSaver = Saver<Pair<Account, Int>?, List<Any?>>(
    save = { pair ->
        pair?.let { (a, count) -> listOf(a.id, a.name, a.type.name, a.bank, a.last4, count) }
    },
    restore = {
        Account(
            it[0] as Long,
            it[1] as String,
            AccountType.valueOf(it[2] as String),
            it[3] as String?,
            it[4] as String?
        ) to
            it[5] as Int
    }
)
