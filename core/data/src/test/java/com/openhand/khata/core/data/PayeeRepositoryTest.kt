package com.openhand.khata.core.data

import com.openhand.khata.core.database.entity.PayeeEntity
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Payee
import com.openhand.khata.core.model.Transaction
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
class PayeeRepositoryTest : RepositoryTest() {
    private val payees by lazy { PayeeRepository(lazyDb) }
    private val transactions by lazy { TransactionRepository(lazyDb) }

    private suspend fun categoryId(seedKey: String) = db.categoryDao().getBySeedKey(seedKey)!!.id

    private suspend fun addPayee(identifier: String, name: String = identifier): Long =
        db.payeeDao().insert(
            PayeeEntity(identifier = identifier, displayName = name, defaultCategoryId = null)
        )

    private suspend fun spend(payee: String): Long = transactions.save(
        Transaction(
            amountPaise = 5_000,
            direction = Direction.DEBIT,
            timestamp = 0,
            payeeName = payee
        )
    )

    @Test
    fun listsPayeesWithDefaultsAndTransactionCounts() = runTest {
        val swiggy = addPayee("swiggy@icici", "Swiggy")
        addPayee("Ramesh")
        payees.save(
            Payee(
                id = swiggy,
                identifier = "ignored",
                displayName = "Swiggy",
                defaultCategoryId = categoryId("food"),
                defaultTags = listOf("online", "Delivery")
            )
        )
        spend("Swiggy")
        spend("swiggy@icici")

        val list = payees.observePayees().first()
        assertEquals(listOf("Ramesh", "Swiggy"), list.map { it.displayName })
        val saved = list[1]
        assertEquals("swiggy@icici", saved.identifier)
        assertEquals(categoryId("food"), saved.defaultCategoryId)
        assertEquals(listOf("Delivery", "online"), saved.defaultTags)
        assertEquals(2, saved.transactionCount)
        assertEquals(0, list[0].transactionCount)
    }

    @Test
    fun findsByNameOrIdentifierAndSkipsArchivedCategories() = runTest {
        val id = addPayee("store@okaxis", "General Store")
        val shopping = categoryId("shopping")
        payees.save(
            Payee(
                id = id,
                identifier = "store@okaxis",
                displayName = "General Store",
                defaultCategoryId = shopping,
                defaultTags = listOf("home")
            )
        )

        val byName = payees.find(" general store ")!!
        assertEquals(id, byName.id)
        assertEquals(shopping, byName.defaultCategoryId)
        assertEquals(listOf("home"), byName.defaultTags)
        assertEquals(id, payees.find("STORE@OKAXIS")!!.id)
        assertNull(payees.find("General"))
        assertNull(payees.find(" "))

        CategoryRepository(lazyDb).setArchived(shopping, true)
        assertNull(payees.find("General Store")!!.defaultCategoryId)
    }

    @Test
    fun editingKeepsTheIdentifier() = runTest {
        val id = addPayee("paytmqr281005050101@paytm")
        payees.save(
            Payee(
                id = id,
                identifier = "something else",
                displayName = " Chai stall ",
                defaultCategoryId = categoryId("food"),
                defaultTags = listOf("office")
            )
        )
        payees.save(payees.find("Chai stall")!!.copy(defaultTags = emptyList()))

        val entity = db.payeeDao().getById(id)!!
        assertEquals("paytmqr281005050101@paytm", entity.identifier)
        assertEquals("Chai stall", entity.displayName)
        assertEquals(emptyList<Long>(), db.payeeDao().defaultTagIds(id))
        assertEquals(id, payees.find("paytmqr281005050101@paytm")!!.id)
    }

    @Test
    fun marksAPayeeAsOwnAccountAndBack() = runTest {
        addPayee("PRIYENDU SINGH")

        payees.save(payees.find("PRIYENDU SINGH")!!.copy(ownAccount = true))
        assertTrue(payees.find("PRIYENDU SINGH")!!.ownAccount)
        assertTrue(payees.observePayees().first().single().ownAccount)

        payees.save(payees.find("PRIYENDU SINGH")!!.copy(ownAccount = false))
        assertFalse(payees.find("PRIYENDU SINGH")!!.ownAccount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsABlankName() = runTest {
        val id = addPayee("Ramesh")
        payees.save(Payee(id = id, identifier = "Ramesh", displayName = " "))
    }

    @Test
    fun mergeMovesTransactionsAndKeepsTheTargetsDefaults() = runTest {
        val keep = addPayee("zomato@hdfc", "Zomato")
        val drop = addPayee("Zomato Ltd")
        payees.save(
            Payee(
                id = keep,
                identifier = "zomato@hdfc",
                displayName = "Zomato",
                defaultCategoryId = categoryId("food"),
                defaultTags = listOf("online")
            )
        )
        payees.save(
            Payee(
                id = drop,
                identifier = "Zomato Ltd",
                displayName = "Zomato Ltd",
                defaultCategoryId = categoryId("shopping"),
                defaultTags = listOf("misc")
            )
        )
        val a = spend("Zomato Ltd")
        val b = spend("Zomato Ltd")
        val c = spend("Zomato")

        payees.merge(drop, keep)

        listOf(a, b, c).forEach { assertEquals(keep, db.transactionDao().getById(it)!!.payeeId) }
        assertNull(db.payeeDao().getById(drop))
        val kept = payees.observePayees().first().single()
        assertEquals(3, kept.transactionCount)
        assertEquals(categoryId("food"), kept.defaultCategoryId)
        assertEquals(listOf("online"), kept.defaultTags)
    }

    @Test
    fun mergingIntoItselfChangesNothing() = runTest {
        val id = addPayee("Ramesh")
        val transaction = spend("Ramesh")

        payees.merge(id, id)

        assertEquals(id, db.transactionDao().getById(transaction)!!.payeeId)
        assertEquals(listOf(id), payees.observePayees().first().map { it.id })
    }
}
