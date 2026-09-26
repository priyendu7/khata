package com.openhand.khata.core.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.openhand.khata.core.database.entity.AccountEntity
import com.openhand.khata.core.database.entity.CategoryEntity
import com.openhand.khata.core.database.entity.PayeeEntity
import com.openhand.khata.core.database.entity.TagEntity
import com.openhand.khata.core.database.entity.TransactionEntity
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.TransactionSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** DAO and constraint tests for the first schema, on the JVM (Robolectric, plain in-memory SQLite). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KhataDatabaseTest {
    private lateinit var db: KhataDatabase
    private var categoryId = 0L
    private var accountId = 0L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(
                context,
                KhataDatabase::class.java
            ).allowMainThreadQueries().build()
        categoryId =
            db.categoryDao().insert(
                CategoryEntity(name = "Food", color = 0xFFC62828.toInt(), icon = "food")
            )
        accountId =
            db.accountDao().insert(
                AccountEntity(
                    name = "HDFC savings",
                    type = AccountType.BANK,
                    bank = "HDFC",
                    last4 = "1234"
                )
            )
    }

    @After
    fun tearDown() = db.close()

    private fun transaction(
        referenceNo: String? = null,
        amountPaise: Long = 25_050,
        direction: Direction = Direction.DEBIT,
        payeeId: Long? = null
    ) = TransactionEntity(
        amountPaise = amountPaise,
        direction = direction,
        timestamp = 1_790_000_000_000,
        accountId = accountId,
        payeeId = payeeId,
        categoryId = categoryId,
        note = "chai",
        referenceNo = referenceNo,
        source = TransactionSource.MANUAL,
        rawSms = null
    )

    @Test
    fun insertsUpdatesAndDeletesATransaction() = runTest {
        val dao = db.transactionDao()
        val id = dao.insert(transaction())

        val saved = dao.getById(id)!!
        assertEquals(25_050L, saved.amountPaise)
        assertEquals(Direction.DEBIT, saved.direction)

        dao.update(saved.copy(amountPaise = 30_000, note = "lunch"))
        assertEquals("lunch", dao.getById(id)!!.note)

        dao.delete(dao.getById(id)!!)
        assertNull(dao.getById(id))
    }

    @Test
    fun referenceNumberIsUniqueWhenPresent() = runTest {
        val dao = db.transactionDao()
        dao.insert(transaction(referenceNo = "UPI123"))

        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking { dao.insert(transaction(referenceNo = "UPI123")) }
        }

        // Any number of transactions may have no reference number.
        dao.insert(transaction(referenceNo = null))
        dao.insert(transaction(referenceNo = null))
        assertEquals(3, dao.observeAll().first().size)
        assertEquals("UPI123", dao.getByReferenceNo("UPI123")!!.referenceNo)
    }

    @Test
    fun largeAmountsKeepEveryPaisa() = runTest {
        // ₹1,00,00,000.99 — ten million rupees and 99 paise.
        val id = db.transactionDao().insert(transaction(amountPaise = 1_000_000_099))
        assertEquals(1_000_000_099L, db.transactionDao().getById(id)!!.amountPaise)
    }

    @Test
    fun enumsAreStoredAsFixedText() = runTest {
        db.transactionDao().insert(
            transaction(direction = Direction.REFUND).copy(source = TransactionSource.CSV)
        )

        db.openHelper.readableDatabase.query(
            "SELECT direction, source FROM transactions"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("refund", cursor.getString(0))
            assertEquals("csv", cursor.getString(1))
        }
        db.openHelper.readableDatabase.query("SELECT type FROM accounts").use { cursor ->
            cursor.moveToFirst()
            assertEquals("bank", cursor.getString(0))
        }
    }

    @Test
    fun tagNamesAreUniqueIgnoringCase() = runTest {
        val dao = db.tagDao()
        val id = dao.insert(TagEntity(name = "Work"))

        assertEquals(-1L, dao.insert(TagEntity(name = "work")))
        assertEquals(id, dao.getByName("WORK")!!.id)
    }

    @Test
    fun transactionTagsCanBeReplacedAndFollowDeletes() = runTest {
        val work = db.tagDao().insert(TagEntity(name = "work"))
        val goa = db.tagDao().insert(TagEntity(name = "trip-goa"))
        val dao = db.transactionDao()
        val id = dao.insert(transaction())

        dao.setTags(id, listOf(work, goa))
        assertEquals(setOf(work, goa), dao.tagIds(id).toSet())

        dao.setTags(id, listOf(goa))
        assertEquals(listOf(goa), dao.tagIds(id))

        db.tagDao().delete(TagEntity(id = goa, name = "trip-goa"))
        assertTrue(dao.tagIds(id).isEmpty())

        dao.setTags(id, listOf(work))
        dao.delete(dao.getById(id)!!)
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM transaction_tags").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    @Test
    fun categoriesAndAccountsInUseCantBeDeleted() = runTest {
        db.transactionDao().insert(transaction())

        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking {
                db.categoryDao().delete(db.categoryDao().getById(categoryId)!!)
            }
        }
        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking {
                db.accountDao().delete(db.accountDao().getById(accountId)!!)
            }
        }
    }

    @Test
    fun deletingAPayeeKeepsItsTransactions() = runTest {
        val payeeId =
            db.payeeDao().insert(
                PayeeEntity(
                    identifier = "generalstore@okhdfc",
                    displayName = "General Store",
                    defaultCategoryId = categoryId
                )
            )
        val id = db.transactionDao().insert(transaction(payeeId = payeeId))

        db.payeeDao().delete(db.payeeDao().getByIdentifier("generalstore@okhdfc")!!)

        assertNull(db.transactionDao().getById(id)!!.payeeId)
    }

    @Test
    fun payeeIdentifiersAreUniqueAndDefaultTagsCanBeReplaced() = runTest {
        val dao = db.payeeDao()
        val payeeId = dao.insert(
            PayeeEntity(
                identifier = "paytmqr123@paytm",
                displayName = "Tea stall",
                defaultCategoryId = null
            )
        )
        val work = db.tagDao().insert(TagEntity(name = "work"))
        val food = db.tagDao().insert(TagEntity(name = "snacks"))

        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking {
                dao.insert(
                    PayeeEntity(
                        identifier = "paytmqr123@paytm",
                        displayName = "Other",
                        defaultCategoryId = null
                    )
                )
            }
        }

        dao.setDefaultTags(payeeId, listOf(work, food))
        dao.setDefaultTags(payeeId, listOf(food))
        assertEquals(listOf(food), dao.defaultTagIds(payeeId))
    }
}
