package com.openhand.khata.core.data

import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountRepositoryTest : RepositoryTest() {
    private val accounts by lazy { AccountRepository(lazyDb) }

    @Test
    fun savesAndUpdatesAccounts() = runTest {
        val id = accounts.save(
            Account(
                name = "  HDFC savings ",
                type = AccountType.BANK,
                bank = " HDFC ",
                last4 = "1234"
            )
        )
        accounts.save(
            Account(id = id, name = "HDFC salary", type = AccountType.BANK, bank = "", last4 = "")
        )

        val saved = accounts.observeAccounts().first().single()
        assertEquals("HDFC salary", saved.name)
        assertNull(saved.bank)
        assertNull(saved.last4)
    }

    @Test
    fun onlyFourDigitsAreAccepted() = runTest {
        listOf("123", "12345", "12a4", "1234567890123456").forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking {
                    accounts.save(
                        Account(name = "Card", type = AccountType.CREDIT_CARD, last4 = bad)
                    )
                }
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                accounts.save(Account(name = "  ", type = AccountType.WALLET))
            }
        }
    }

    @Test
    fun deletingMovesTransactionsToAnotherAccount() = runTest {
        val old = accounts.save(Account(name = "Old card", type = AccountType.CREDIT_CARD))
        val new = accounts.save(Account(name = "New card", type = AccountType.CREDIT_CARD))
        val t1 = addTransaction(old)
        val t2 = addTransaction(old)
        assertEquals(2, accounts.transactionCount(old))

        accounts.delete(old, moveTransactionsTo = new)

        assertEquals(listOf("New card"), accounts.observeAccounts().first().map { it.name })
        assertEquals(new, db.transactionDao().getById(t1)!!.accountId)
        assertEquals(new, db.transactionDao().getById(t2)!!.accountId)
    }

    @Test
    fun deletingCanLeaveTransactionsWithoutAnAccount() = runTest {
        val wallet = accounts.save(Account(name = "Paytm", type = AccountType.WALLET))
        val t = addTransaction(wallet)

        accounts.delete(wallet, moveTransactionsTo = null)

        assertNull(db.transactionDao().getById(t)!!.accountId)
        assertEquals(0, accounts.observeAccounts().first().size)
    }
}
