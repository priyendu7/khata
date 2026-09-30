package com.openhand.khata.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Each migration on the JVM, so CI runs it: create the old version with data, migrate, validate
 * against the exported schema, and check the data survived. (MigrationTest in androidTest runs
 * the same on a device.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationsTest {
    private val dbName = "migrations-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KhataDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun version1To2AddsUnparsedSmsAndKeepsTransactions() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                "INSERT INTO categories (id, name, seed_key, color, icon, archived) " +
                    "VALUES (1, NULL, 'uncategorized', 0, 'category', 0)"
            )
            db.execSQL(
                "INSERT INTO transactions (id, amount_paise, direction, timestamp, category_id, " +
                    "source, needs_review) VALUES (7, 36600, 'debit', 1790000000000, 1, 'sms', 1)"
            )
        }

        helper.runMigrationsAndValidate(dbName, 2, true, *KhataMigrations.ALL).use { db ->
            db.query("SELECT amount_paise, needs_review FROM transactions WHERE id = 7").use {
                it.moveToFirst()
                assertEquals(36600L, it.getLong(0))
                assertEquals(1, it.getInt(1))
            }
            db.execSQL(
                "INSERT INTO unparsed_sms (sender, body, received_at) " +
                    "VALUES ('JM-KOTAKB-S', 'Rs.2,000 withdrawn at ATM', 1790000000000)"
            )
            db.query("SELECT COUNT(*) FROM unparsed_sms").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
        }
    }
}
