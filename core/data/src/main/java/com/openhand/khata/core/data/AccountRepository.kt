package com.openhand.khata.core.data

import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.model.Account
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class AccountRepository @Inject constructor(private val db: Lazy<KhataDatabase>) {
    fun observeAccounts(): Flow<List<Account>> = db.observe {
        it.accountDao().observeAll()
    }.map { accounts -> accounts.map { it.toModel() } }

    /**
     * Adds a new account (id 0) or updates an existing one, and returns its id. Blank bank and
     * last-4 fields are stored as empty; last 4 must be exactly four digits.
     */
    suspend fun save(account: Account): Long {
        val clean = account.copy(
            name = account.name.trim(),
            bank = account.bank?.trim()?.ifEmpty { null },
            last4 = account.last4?.trim()?.ifEmpty { null }
        )
        require(clean.name.isNotEmpty()) { "Account name is empty" }
        require(Account.isValidLast4(clean.last4)) { "Last 4 must be exactly 4 digits" }
        return db.io { database ->
            val dao = database.accountDao()
            if (clean.id == 0L) {
                dao.insert(clean.toEntity())
            } else {
                dao.update(clean.toEntity())
                clean.id
            }
        }
    }

    suspend fun transactionCount(accountId: Long): Int = db.io {
        it.accountDao().transactionCount(accountId)
    }

    /**
     * Deletes an account. Its transactions move to [moveTransactionsTo], or to no account when
     * that's null, so no transaction is ever lost.
     */
    suspend fun delete(accountId: Long, moveTransactionsTo: Long?) {
        require(moveTransactionsTo != accountId) {
            "Can't move transactions to the account being deleted"
        }
        db.io { it.accountDao().deleteMovingTransactions(accountId, moveTransactionsTo) }
    }
}
