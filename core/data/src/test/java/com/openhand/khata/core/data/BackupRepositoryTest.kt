package com.openhand.khata.core.data

import app.cash.turbine.test
import com.openhand.khata.core.database.entity.AccountEntity
import com.openhand.khata.core.database.entity.CategoryEntity
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Transaction
import com.openhand.khata.core.model.TransactionRecord
import com.openhand.khata.core.model.TransactionSource
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRepositoryTest : RepositoryTest() {
    private val backup by lazy { BackupRepository(lazyDb) }
    private val transactions by lazy { TransactionRepository(lazyDb) }
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun record(
        amount: Long = 25_000,
        time: String = "2026-09-27T09:00:00",
        block: TransactionRecord.() -> TransactionRecord = { this }
    ) = TransactionRecord(timestamp = at(time), amountPaise = amount, direction = Direction.DEBIT)
        .block()

    private val names: (Category) -> String =
        { it.name ?: it.seedKey.orEmpty() }

    @Test
    fun importsNamesAsAccountsCategoriesPayeesAndTags() = runTest {
        val result = backup.import(
            listOf(
                record {
                    copy(
                        account = "HDFC",
                        payee = "swiggy@icici",
                        payeeName = "Swiggy",
                        category = "Khana",
                        tags = listOf("office", "Office", "team"),
                        note = "Lunch",
                        referenceNo = "4269"
                    )
                },
                record(time = "2026-09-28T09:00:00") {
                    copy(account = "hdfc", payeeName = "SWIGGY", category = "Fuel")
                }
            ),
            zone,
            categoryAliases = mapOf("khana" to "food"),
            newCategoryColors = listOf(0xFF00FF00.toInt())
        )
        assertEquals(ImportResult(added = 2, duplicates = 0), result)

        val saved = db.transactionDao().observeAll().first().sortedBy { it.timestamp }
        assertEquals(1, db.accountDao().observeAll().first().size)
        assertEquals(setOf(saved[0].accountId), setOf(saved[1].accountId))
        assertEquals(saved[0].payeeId, saved[1].payeeId)
        val payee = db.payeeDao().getById(saved[0].payeeId!!)!!
        assertEquals("swiggy@icici", payee.identifier)
        assertEquals("Swiggy", payee.displayName)
        assertEquals("food", db.categoryDao().getById(saved[0].categoryId)!!.seedKey)
        val fuel = db.categoryDao().getById(saved[1].categoryId)!!
        assertEquals("Fuel", fuel.name)
        assertEquals(0xFF00FF00.toInt(), fuel.color)
        assertEquals(listOf("office", "team"), db.transactionDao().tagNames(saved[0].id))
        assertEquals("4269", saved[0].referenceNo)
        assertEquals(TransactionSource.CSV, saved[0].source)
        assertEquals("Lunch", saved[0].note)
    }

    @Test
    fun blankCategoryIsUncategorizedAndSeedKeyMatchesDefault() = runTest {
        backup.import(
            listOf(
                record(),
                record(amount = 100) { copy(category = "Groceries") },
                record(amount = 200) { copy(category = "Petrol") }
            ),
            zone
        )
        val saved = db.transactionDao().observeAll().first()
        val categories = saved.map { db.categoryDao().getById(it.categoryId)!!.seedKey }.toSet()
        // "Groceries" is a default category's key; "Petrol" is new.
        assertEquals(setOf("uncategorized", "groceries", null), categories)
        assertEquals(11, db.categoryDao().getAll().size)
    }

    @Test
    fun userNameWinsOverDefaultAlias() = runTest {
        val custom = db.categoryDao().insert(
            CategoryEntity(name = "Food", color = 0, icon = "food")
        )
        backup.import(
            listOf(record { copy(category = "FOOD") }),
            zone,
            categoryAliases = mapOf("food" to "food")
        )
        assertEquals(custom, db.transactionDao().observeAll().first().single().categoryId)
    }

    @Test
    fun importingTwiceAddsNothingTheSecondTime() = runTest {
        val file = listOf(
            record { copy(referenceNo = "111") },
            record { copy(payeeName = "Chai") },
            record { copy(payeeName = "Chai") },
            record { copy(note = "Auto") },
            record(time = "2026-09-27T23:59:00") { copy(direction = Direction.REFUND) }
        )
        assertEquals(ImportResult(5, 0), backup.import(file, zone))
        assertEquals(List(5) { true }, backup.findDuplicates(file, zone))
        assertEquals(ImportResult(0, 5), backup.import(file, zone))
        assertEquals(5, db.transactionDao().observeAll().first().size)
    }

    @Test
    fun duplicatesByReferenceOrByDayAmountAndPayee() = runTest {
        transactions.save(
            Transaction(
                amountPaise = 25_000,
                direction = Direction.DEBIT,
                timestamp = at("2026-09-27T20:00:00"),
                payeeName = "Chai Stall"
            )
        )
        val checks = backup.findDuplicates(
            listOf(
                // Same day, amount and payee (ignoring case and time of day).
                record { copy(payeeName = "chai stall") },
                // One saved transaction matches one row only.
                record { copy(payeeName = "Chai Stall") },
                // A different day, amount, direction or payee isn't a duplicate.
                record(time = "2026-09-28T09:00:00") { copy(payeeName = "Chai Stall") },
                record(amount = 25_001) { copy(payeeName = "Chai Stall") },
                record { copy(payeeName = "Chai Stall", direction = Direction.REFUND) },
                record { copy(payeeName = "Tea Stall") },
                // A reference number is checked on its own, also within the file.
                record { copy(payeeName = "Chai Stall", referenceNo = "9") },
                record { copy(referenceNo = "9") }
            ),
            zone
        )
        assertEquals(listOf(true, false, false, false, false, false, false, true), checks)
    }

    @Test
    fun bothSidesOfATransferWithOneReferenceAreImported() = runTest {
        val sides = listOf(
            record { copy(direction = Direction.TRANSFER, account = "Kotak", referenceNo = "7") },
            record { copy(direction = Direction.TRANSFER, account = "HDFC", referenceNo = "7") }
        )

        assertEquals(ImportResult(2, 0), backup.import(sides, zone))
        assertEquals(List(2) { true }, backup.findDuplicates(sides, zone))
    }

    @Test
    fun exportsNamesOldestFirstWithinTheRange() = runTest {
        val cash = db.accountDao().insert(
            AccountEntity(
                name = "Cash",
                type = AccountType.WALLET,
                bank = null,
                last4 = null
            )
        )
        transactions.save(
            Transaction(
                amountPaise = 5_000,
                direction = Direction.DEBIT,
                timestamp = at("2026-09-02T10:00:00"),
                accountId = cash,
                payeeName = "Café",
                tags = listOf("b", "A"),
                note = "Coffee"
            )
        )
        transactions.save(
            Transaction(
                amountPaise = 1_000,
                direction = Direction.CREDIT,
                timestamp = at("2026-09-01T10:00:00")
            )
        )
        transactions.save(
            Transaction(
                amountPaise = 9_000,
                direction = Direction.DEBIT,
                timestamp = at("2026-10-01T00:00:00")
            )
        )
        val september = backup.export(
            from = at("2026-09-01T00:00:00"),
            until = at("2026-10-01T00:00:00"),
            categoryName = names
        )
        assertEquals(listOf(1_000L, 5_000L), september.map { it.amountPaise })
        val coffee = september[1]
        assertEquals("Cash", coffee.account)
        assertEquals("Café", coffee.payee)
        assertEquals("Café", coffee.payeeName)
        assertEquals("uncategorized", coffee.category)
        assertEquals(listOf("A", "b"), coffee.tags)
        assertEquals("Coffee", coffee.note)
        assertNull(coffee.referenceNo)
        assertEquals(3, backup.export(categoryName = names).size)
    }

    @Test
    fun remembersTheLastExport() = runTest {
        backup.observeLastExport().test {
            assertNull(awaitItem())
            backup.markExported(1_790_000_000_000)
            assertEquals(1_790_000_000_000, awaitItem())
        }
    }
}
