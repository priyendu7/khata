package com.openhand.khata.core.data

import androidx.room.withTransaction
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.CardCycles
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
     * last-4 fields are stored as empty; last 4 must be exactly four digits. Only a debit card
     * keeps a linked account, and only a bank account it can be; an account that stops being a
     * bank account loses its cards (#155).
     */
    suspend fun save(account: Account): Long {
        val clean = account.copy(
            name = account.name.trim(),
            bank = account.bank?.trim()?.ifEmpty { null },
            last4 = account.last4?.trim()?.ifEmpty { null },
            linkedAccountId = account.linkedAccountId
                ?.takeIf { account.type == AccountType.DEBIT_CARD && it != account.id }
        )
        require(clean.name.isNotEmpty()) { "Account name is empty" }
        require(Account.isValidLast4(clean.last4)) { "Last 4 must be exactly 4 digits" }
        require(CardCycles.isValidBillingDay(clean.billingDay)) { "Billing day must be 1 to 31" }
        return db.io { database ->
            database.withTransaction {
                val dao = database.accountDao()
                val linked = clean.linkedAccountId
                    ?.takeIf { dao.getById(it)?.type == AccountType.BANK }
                val entity = clean.copy(linkedAccountId = linked).toEntity()
                if (clean.id == 0L) {
                    dao.insert(entity)
                } else {
                    dao.update(entity)
                    if (clean.type != AccountType.BANK) dao.unlinkCardsOf(clean.id)
                    clean.id
                }
            }
        }
    }

    /** Links debit card [cardId] to bank account [accountId] (#155), or unlinks it with null. */
    suspend fun setLinkedAccount(cardId: Long, accountId: Long?) {
        db.io { database ->
            val dao = database.accountDao()
            require(dao.getById(cardId)?.type == AccountType.DEBIT_CARD) { "Only debit cards link" }
            require(accountId == null || dao.getById(accountId)?.type == AccountType.BANK) {
                "Debit cards link to bank accounts"
            }
            dao.setLinkedAccount(cardId, accountId)
        }
    }

    /** Sets a credit card's billing day (#154), 1–31, or clears it with null. */
    suspend fun setBillingDay(accountId: Long, day: Int?) {
        require(CardCycles.isValidBillingDay(day)) { "Billing day must be 1 to 31" }
        db.io { it.accountDao().setBillingDay(accountId, day) }
    }

    suspend fun transactionCount(accountId: Long): Int = db.io {
        it.accountDao().transactionCount(accountId)
    }

    /**
     * Deletes an account. Its transactions and card statements move to [moveTransactionsTo], or
     * its transactions to no account when that's null, so no transaction is ever lost.
     */
    suspend fun delete(accountId: Long, moveTransactionsTo: Long?) {
        require(moveTransactionsTo != accountId) {
            "Can't move transactions to the account being deleted"
        }
        db.io { it.accountDao().deleteMovingTransactions(accountId, moveTransactionsTo) }
    }
}
