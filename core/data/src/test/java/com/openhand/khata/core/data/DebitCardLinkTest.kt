package com.openhand.khata.core.data

import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.SmsTransaction
import com.openhand.khata.core.model.Transaction
import com.openhand.khata.core.model.TransactionFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A debit card linked to its bank account (#155): the account's figures include the card's, and
 * nothing is counted twice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DebitCardLinkTest : RepositoryTest() {
    private val transactions by lazy { TransactionRepository(lazyDb) }
    private val accounts by lazy { AccountRepository(lazyDb) }
    private val cards by lazy { CardRepository(lazyDb, transactions) }
    private val importer by lazy { SmsImporter(lazyDb) }

    private suspend fun account(
        name: String,
        type: AccountType = AccountType.BANK,
        last4: String = "1234",
        linkedTo: Long? = null
    ) = accounts.save(
        Account(name = name, type = type, bank = "HDFC", last4 = last4, linkedAccountId = linkedTo)
    )

    private suspend fun spend(amount: Long, accountId: Long?, category: String = "food") =
        transactions.save(
            Transaction(
                amountPaise = amount,
                direction = Direction.DEBIT,
                timestamp = MONTH_START,
                accountId = accountId,
                categoryId = db.categoryDao().getBySeedKey(category)!!.id
            )
        )

    private suspend fun linkOf(id: Long) = db.accountDao().getById(id)!!.linkedAccountId

    private fun debitSms(card: String = "5678", account: String? = "1234", at: Long = MONTH_START) =
        SmsTransaction(
            amountPaise = 500_00,
            direction = Direction.DEBIT,
            timestamp = at,
            bank = "HDFC",
            accountType = AccountType.DEBIT_CARD,
            accountLast4 = card,
            payee = "Big Bazaar",
            referenceNo = null,
            rawSms = "Rs.500 spent on HDFC Debit Card XX$card at Big Bazaar from A/c XX$account",
            linkedAccountLast4 = account
        )

    @Test
    fun aLinkedCardsSpendingCountsUnderItsAccountOnce() = runTest {
        val savings = account("HDFC Savings")
        val card = account("HDFC Debit", AccountType.DEBIT_CARD, "5678", linkedTo = savings)
        val other = account("Kotak", last4 = "9999")
        spend(1_000_00, savings)
        spend(400_00, card)
        spend(250_00, other)

        val spending = transactions.observeAccountSpending(MONTH_START, MONTH_END).first()
        assertEquals(
            listOf("HDFC Savings" to 1_400_00L, "Kotak" to 250_00L),
            spending.map { it.account?.name to it.spentPaise }
        )
        assertEquals(
            transactions.observeTotals(MONTH_START, MONTH_END).first().spentPaise,
            spending.sumOf { it.spentPaise }
        )
    }

    @Test
    fun anAccountsBreakdownAndListIncludeItsCards() = runTest {
        val savings = account("HDFC Savings")
        val card = account("HDFC Debit", AccountType.DEBIT_CARD, "5678", linkedTo = savings)
        spend(1_000_00, savings, "food")
        val cardSpend = spend(400_00, card, "shopping")
        val savingsAccount = accounts.observeAccounts().first().first { it.id == savings }
        val cardAccount = accounts.observeAccounts().first().first { it.id == card }

        val byCategory = transactions
            .observeCategorySpendingForAccount(MONTH_START, MONTH_END, savingsAccount).first()
        assertEquals(1_400_00L, byCategory.sumOf { it.spentPaise })
        assertEquals(
            2,
            transactions.observe(TransactionFilter(accountId = savings)).first().size
        )
        // The card's own page shows only the card's.
        assertEquals(
            listOf(400_00L),
            transactions.observeCategorySpendingForAccount(MONTH_START, MONTH_END, cardAccount)
                .first().map { it.spentPaise }
        )
        assertEquals(
            listOf(cardSpend),
            transactions.observe(TransactionFilter(accountId = card)).first().map { it.id }
        )
    }

    @Test
    fun debitCardsShowTheirAccountAndOwnSpending() = runTest {
        val savings = account("HDFC Savings")
        val card = account("HDFC Debit", AccountType.DEBIT_CARD, "5678", linkedTo = savings)
        account("Spare Debit", AccountType.DEBIT_CARD, "4321")
        spend(1_000_00, savings)
        spend(400_00, card)

        val debit = cards.observeDebitCards(MONTH_START, MONTH_END).first()
        assertEquals(
            listOf(Triple("HDFC Debit", "HDFC Savings", 400_00L), Triple("Spare Debit", null, 0L)),
            debit.map { Triple(it.card.name, it.linkedAccount?.name, it.spentPaise) }
        )
    }

    @Test
    fun onlyADebitCardLinksAndOnlyToABankAccount() = runTest {
        val savings = account("HDFC Savings")
        val credit = account("HDFC Credit", AccountType.CREDIT_CARD, "1111")
        val card = account("HDFC Debit", AccountType.DEBIT_CARD, "5678")

        accounts.setLinkedAccount(card, savings)
        assertEquals(savings, linkOf(card))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { accounts.setLinkedAccount(card, credit) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { accounts.setLinkedAccount(savings, savings) }
        }
        // A link to something that isn't a bank account isn't saved.
        val other = account("Other Debit", AccountType.DEBIT_CARD, "2222", linkedTo = credit)
        assertNull(linkOf(other))
        // Nor is one on an account that isn't a debit card.
        val wallet = account("Wallet", AccountType.WALLET, "3333", linkedTo = savings)
        assertNull(linkOf(wallet))
    }

    @Test
    fun aCardLosesItsLinkWhenTheAccountGoesOrStopsBeingABank() = runTest {
        val savings = account("HDFC Savings")
        val card = account("HDFC Debit", AccountType.DEBIT_CARD, "5678", linkedTo = savings)

        accounts.save(
            Account(savings, "HDFC Wallet", AccountType.WALLET, "HDFC", "1234")
        )
        assertNull(linkOf(card))

        accounts.save(Account(savings, "HDFC Savings", AccountType.BANK, "HDFC", "1234"))
        accounts.setLinkedAccount(card, savings)
        accounts.delete(savings, moveTransactionsTo = null)
        assertNull(linkOf(card))
    }

    @Test
    fun anSmsNamingTheAccountLinksANewCardToIt() = runTest {
        val savings = account("HDFC Savings")

        importer.import(debitSms())

        val card = db.accountDao().getByLast4("5678").single()
        assertEquals(AccountType.DEBIT_CARD, card.type)
        assertEquals(savings, card.linkedAccountId)
    }

    @Test
    fun anSmsNamingAnUnknownAccountMakesItAndLinksTheCard() = runTest {
        importer.import(debitSms())

        val account = db.accountDao().getByLast4("1234").single()
        assertEquals(AccountType.BANK, account.type)
        assertEquals("HDFC", account.bank)
        assertEquals(account.id, db.accountDao().getByLast4("5678").single().linkedAccountId)
    }

    @Test
    fun aCardAlreadyLinkedKeepsItsAccountAndOneWithoutAnAccountInTheSmsStaysUnlinked() = runTest {
        val savings = account("HDFC Savings")
        val other = account("HDFC Salary", last4 = "8888")
        val card = account("HDFC Debit", AccountType.DEBIT_CARD, "5678", linkedTo = other)

        importer.import(debitSms())
        assertEquals(other, linkOf(card))

        importer.import(debitSms(card = "4321", account = null, at = MONTH_START + DAY))
        assertNull(db.accountDao().getByLast4("4321").single().linkedAccountId)
        assertEquals(1, db.accountDao().getByLast4("1234").size)
        assertEquals(savings, db.accountDao().getByLast4("1234").single().id)
    }

    private companion object {
        const val DAY = 86_400_000L
        const val MONTH_START = 1_788_201_000_000L
        const val MONTH_END = MONTH_START + 30 * DAY
    }
}
