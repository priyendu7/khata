package com.openhand.khata.core.data

import app.cash.turbine.test
import com.openhand.khata.core.database.entity.PayeeEntity
import com.openhand.khata.core.database.entity.TransactionEntity
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Transaction
import com.openhand.khata.core.model.TransactionFilter
import com.openhand.khata.core.model.TransactionSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransactionRepositoryTest : RepositoryTest() {
    private val transactions by lazy { TransactionRepository(lazyDb) }

    private fun expense(
        amount: Long = 25_000,
        at: Long = DAY,
        block: Transaction.() -> Transaction = {
            this
        }
    ) = Transaction(amountPaise = amount, direction = Direction.DEBIT, timestamp = at).block()

    @Test
    fun savesPayeeTagsAndDefaultsToUncategorized() = runTest {
        val id = transactions.save(
            expense { copy(payeeName = " Swiggy ", tags = listOf("Work", "work", " "), note = " ") }
        )

        val saved = transactions.get(id)!!
        assertEquals("Swiggy", saved.payeeName)
        assertEquals(listOf("Work"), saved.tags)
        assertEquals(uncategorizedId(), saved.categoryId)
        assertNull(saved.note)
        assertEquals(TransactionSource.MANUAL, db.transactionDao().getById(id)!!.source)
    }

    @Test
    fun reusesAPayeeByNameOrIdentifierIgnoringCase() = runTest {
        val upi = db.payeeDao().insert(
            PayeeEntity(
                identifier = "store@okaxis",
                displayName = "General Store",
                defaultCategoryId = null
            )
        )
        val a = transactions.save(expense { copy(payeeName = "general store") })
        val b = transactions.save(expense { copy(payeeName = "STORE@OKAXIS") })

        assertEquals(upi, db.transactionDao().getById(a)!!.payeeId)
        assertEquals(upi, db.transactionDao().getById(b)!!.payeeId)
    }

    @Test
    fun remembersDefaultsForANewPayee() = runTest {
        val food = db.categoryDao().getBySeedKey("food")!!.id
        transactions.save(
            expense { copy(payeeName = "Swiggy", categoryId = food, tags = listOf("online")) },
            rememberPayeeDefaults = true
        )

        val payee = PayeeRepository(lazyDb).find("Swiggy")!!
        assertEquals(food, payee.defaultCategoryId)
        assertEquals(listOf("online"), payee.defaultTags)
    }

    @Test
    fun overridingTheCategoryKeepsTheSavedDefaults() = runTest {
        val food = db.categoryDao().getBySeedKey("food")!!.id
        val groceries = db.categoryDao().getBySeedKey("groceries")!!.id
        transactions.save(
            expense { copy(payeeName = "paytmqr123@paytm", categoryId = food) },
            rememberPayeeDefaults = true
        )

        val id = transactions.save(
            expense { copy(payeeName = "paytmqr123@paytm", categoryId = groceries) },
            rememberPayeeDefaults = true
        )

        assertEquals(groceries, transactions.get(id)!!.categoryId)
        val payee = PayeeRepository(lazyDb).find("paytmqr123@paytm")!!
        assertEquals(food, payee.defaultCategoryId)
        assertEquals(emptyList<String>(), payee.defaultTags)
    }

    @Test
    fun remembersNothingWhenAskedNotToOrWhenThereIsNothingToRemember() = runTest {
        val food = db.categoryDao().getBySeedKey("food")!!.id
        val uncategorized = uncategorizedId()
        transactions.save(expense { copy(payeeName = "Ramesh", categoryId = food) })
        transactions.save(expense { copy(payeeName = "Ramesh") }, rememberPayeeDefaults = true)
        transactions.save(
            expense { copy(payeeName = "Ramesh", categoryId = uncategorized) },
            rememberPayeeDefaults = true
        )

        val payee = PayeeRepository(lazyDb).find("Ramesh")!!
        assertNull(payee.defaultCategoryId)
        assertEquals(emptyList<String>(), payee.defaultTags)

        // A payee without defaults (like ones saved before payee memory) learns them on next use.
        transactions.save(
            expense { copy(payeeName = "ramesh", categoryId = food) },
            rememberPayeeDefaults = true
        )
        assertEquals(food, PayeeRepository(lazyDb).find("Ramesh")!!.defaultCategoryId)
    }

    @Test
    fun updateReplacesTagsAndKeepsSmsFields() = runTest {
        val id = db.transactionDao().insert(
            TransactionEntity(
                amountPaise = 10_000,
                direction = Direction.DEBIT,
                timestamp = DAY,
                accountId = null,
                payeeId = null,
                categoryId = uncategorizedId(),
                note = null,
                referenceNo = "UPI123",
                source = TransactionSource.SMS,
                rawSms = "Rs 100 debited",
                needsReview = true
            )
        )
        db.transactionDao().setTags(id, TagRepository(lazyDb).getOrCreate(listOf("old")))

        transactions.save(
            transactions.get(id)!!.copy(amountPaise = 12_000, tags = listOf("new"), note = "lunch")
        )

        val entity = db.transactionDao().getById(id)!!
        assertEquals(12_000L, entity.amountPaise)
        assertEquals("UPI123", entity.referenceNo)
        assertEquals(TransactionSource.SMS, entity.source)
        assertEquals("Rs 100 debited", entity.rawSms)
        assertFalse(entity.needsReview)
        assertEquals(listOf("new"), transactions.get(id)!!.tags)
    }

    @Test
    fun filtersCombine() = runTest {
        val cash = AccountRepository(lazyDb).save(Account(name = "Cash", type = AccountType.WALLET))
        val food = db.categoryDao().getBySeedKey("food")!!.id
        val lunch = transactions.save(
            expense(at = DAY) {
                copy(
                    categoryId = food,
                    accountId = cash,
                    tags = listOf("office"),
                    note = "Lunch 50% off"
                )
            }
        )
        val dinner = transactions.save(
            expense(at = 2 * DAY) {
                copy(categoryId = food, payeeName = "Dhaba", tags = listOf("family"))
            }
        )
        val taxi = transactions.save(
            expense(at = 3 * DAY) {
                copy(accountId = cash, tags = listOf("office"))
            }
        )
        val officeTag = db.tagDao().getByName("office")!!.id

        suspend fun ids(filter: TransactionFilter) = transactions.observe(filter).first().map {
            it.id
        }

        assertEquals(listOf(taxi, dinner, lunch), ids(TransactionFilter()))
        assertEquals(listOf(dinner, lunch), ids(TransactionFilter(categoryId = food)))
        assertEquals(listOf(taxi, lunch), ids(TransactionFilter(tagId = officeTag)))
        assertEquals(listOf(lunch), ids(TransactionFilter(categoryId = food, accountId = cash)))
        assertEquals(listOf(dinner), ids(TransactionFilter(from = 2 * DAY, until = 3 * DAY)))
        assertEquals(listOf(dinner), ids(TransactionFilter(query = "dhab")))
        assertEquals(listOf(lunch), ids(TransactionFilter(query = "50%")))
        assertEquals(emptyList<Long>(), ids(TransactionFilter(query = "_")))
        assertEquals(
            emptyList<Long>(),
            ids(TransactionFilter(query = "lunch", accountId = null, tagId = 999))
        )
    }

    @Test
    fun listItemsCarryTheirDetails() = runTest {
        val cash = AccountRepository(lazyDb).save(Account(name = "Cash", type = AccountType.WALLET))
        transactions.save(
            expense {
                copy(accountId = cash, payeeName = "Dhaba", tags = listOf("b", "A"))
            }
        )

        val item = transactions.observe().first().single()
        assertEquals("Dhaba", item.payeeName)
        assertEquals("Cash", item.accountName)
        assertEquals("uncategorized", item.category.seedKey)
        assertEquals(listOf("A", "b"), item.tags)
    }

    @Test
    fun listUpdatesAsTransactionsChange() = runTest {
        transactions.observe().test {
            assertEquals(emptyList<Long>(), awaitItem().map { it.id })
            val id = transactions.save(expense())
            assertEquals(listOf(25_000L), awaitItem().map { it.amountPaise })
            transactions.save(transactions.get(id)!!.copy(amountPaise = 30_000))
            assertEquals(listOf(30_000L), awaitItem().map { it.amountPaise })
            transactions.delete(id)
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
