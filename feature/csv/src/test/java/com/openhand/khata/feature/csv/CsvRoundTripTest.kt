package com.openhand.khata.feature.csv

import android.content.Context
import android.content.res.Configuration
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.openhand.khata.core.data.AccountRepository
import com.openhand.khata.core.data.BackupRepository
import com.openhand.khata.core.data.CategoryRepository
import com.openhand.khata.core.data.ImportResult
import com.openhand.khata.core.data.TransactionRepository
import com.openhand.khata.core.database.DefaultCategorySeeder
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Transaction
import dagger.Lazy
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Export, then import into an empty database, gives back the same data (PRD feature 6). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CsvRoundTripTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("Asia/Kolkata")
    private lateinit var source: KhataDatabase
    private lateinit var target: KhataDatabase

    private fun database() = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java)
        .addCallback(DefaultCategorySeeder.callback)
        .allowMainThreadQueries()
        .build()

    @Before
    fun open() {
        source = database()
        target = database()
    }

    @After
    fun close() {
        source.close()
        target.close()
    }

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private suspend fun fill(db: KhataDatabase) {
        val transactions = TransactionRepository(Lazy { db })
        val categories = CategoryRepository(Lazy { db })
        val all = categories.observeAll().first()
        val food = all.first { it.seedKey == "food" }
        // A renamed default category and a new one.
        categories.save(food.copy(name = "Khana-peena"))
        val fuel = categories.save(Category(name = "Fuel", color = 1, icon = "fuel"))
        val card = AccountRepository(Lazy { db }).save(
            Account(name = "HDFC Card", type = AccountType.CREDIT_CARD, last4 = "1234")
        )
        val groceries = all.first { it.seedKey == "groceries" }.id
        listOf(
            Transaction(
                amountPaise = 2_050,
                direction = Direction.DEBIT,
                timestamp = at("2026-09-01T08:05:00"),
                accountId = card,
                payeeName = "चाय वाला",
                categoryId = food.id,
                tags = listOf("office", "snacks"),
                note = "Chai, samosa\nand \"biscuits\""
            ),
            Transaction(
                amountPaise = 350_000,
                direction = Direction.DEBIT,
                timestamp = at("2026-09-03T18:30:00"),
                categoryId = groceries,
                payeeName = "DMart"
            ),
            Transaction(
                amountPaise = 50_000,
                direction = Direction.REFUND,
                timestamp = at("2026-09-04T12:00:00"),
                categoryId = groceries,
                payeeName = "DMart"
            ),
            Transaction(
                amountPaise = 8_500_000,
                direction = Direction.CREDIT,
                timestamp = at("2026-09-30T09:00:00"),
                note = "Salary"
            ),
            Transaction(
                amountPaise = 1_200_000,
                direction = Direction.TRANSFER,
                timestamp = at("2026-09-15T21:00:00"),
                accountId = card,
                note = "Card bill"
            ),
            Transaction(
                amountPaise = 400_000,
                direction = Direction.DEBIT,
                timestamp = at("2026-09-20T07:45:00"),
                categoryId = fuel
            )
        ).forEach { transactions.save(it) }
    }

    private suspend fun exportText(db: KhataDatabase, names: CategoryNames): String {
        val records = BackupRepository(Lazy { db }).export(categoryName = names::nameOf)
        return KhataCsvFormat.write(records, zone)
    }

    private suspend fun import(
        db: KhataDatabase,
        text: String,
        names: CategoryNames
    ): ImportResult {
        val rows = KhataCsvFormat.parse(Csv.parse(text), zone)
        val records = rows.map { (it as ParsedRow.Valid).record }
        return BackupRepository(Lazy { db }).import(records, zone, names.aliases, listOf(0))
    }

    private fun localized(language: String): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language))
        return context.createConfigurationContext(config)
    }

    @Test
    fun exportThenImportGivesBackTheSameData() = runTest {
        val names = CategoryNames.from(localized("en"))
        fill(source)
        val text = exportText(source, names)

        assertEquals(ImportResult(added = 6, duplicates = 0), import(target, text, names))
        assertEquals(text, exportText(target, names))

        val from = at("2026-09-01T00:00:00")
        val until = at("2026-10-01T00:00:00")
        assertEquals(
            TransactionRepository(Lazy { source }).observeTotals(from, until).first(),
            TransactionRepository(Lazy { target }).observeTotals(from, until).first()
        )
        assertEquals(
            listOf("office", "snacks"),
            target.tagDao().observeAll().first().map { it.name }
        )
    }

    @Test
    fun importingTheSameFileTwiceAddsNothing() = runTest {
        val names = CategoryNames.from(localized("en"))
        fill(source)
        val text = exportText(source, names)
        import(target, text, names)
        assertEquals(ImportResult(added = 0, duplicates = 6), import(target, text, names))
        assertEquals(6, target.transactionDao().observeAll().first().size)
    }

    @Test
    fun aHindiExportImportsOnAnEnglishPhone() = runTest {
        fill(source)
        val hindiText = exportText(source, CategoryNames.from(localized("hi")))
        assertEquals(true, hindiText.contains("किराना"))

        val english = CategoryNames.from(localized("en"))
        import(target, hindiText, english)
        assertEquals(exportText(source, english), exportText(target, english))
    }
}
