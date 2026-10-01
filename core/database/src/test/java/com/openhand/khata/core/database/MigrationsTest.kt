package com.openhand.khata.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun version2To3AddsCustomParsersAndKeepsUnparsedSms() {
        helper.createDatabase(dbName, 2).use { db ->
            db.execSQL(
                "INSERT INTO unparsed_sms (sender, body, received_at) " +
                    "VALUES ('JM-KOTAKB-S', 'Rs.2,000 withdrawn at ATM', 1790000000000)"
            )
        }

        helper.runMigrationsAndValidate(dbName, 3, true, *KhataMigrations.ALL).use { db ->
            db.query("SELECT body FROM unparsed_sms").use {
                it.moveToFirst()
                assertEquals("Rs.2,000 withdrawn at ATM", it.getString(0))
            }
            db.execSQL(
                "INSERT INTO custom_parsers (rule_id, bank, code, enabled, added_at) " +
                    "VALUES ('hdfc-upi', 'HDFC', 'khata1:e30', 1, 1790000000000)"
            )
            db.query("SELECT rule_id, enabled FROM custom_parsers").use {
                it.moveToFirst()
                assertEquals("hdfc-upi", it.getString(0))
                assertEquals(1, it.getInt(1))
            }
        }
    }

    @Test
    fun version3To4AddsIgnoreRulesAndKeepsCustomParsersAndUnparsedSms() {
        helper.createDatabase(dbName, 3).use { db ->
            db.execSQL(
                "INSERT INTO unparsed_sms (sender, body, received_at) " +
                    "VALUES ('VM-HDFCBK-S', 'Rs.450 spent on card', 1790000000000)"
            )
            db.execSQL(
                "INSERT INTO custom_parsers (rule_id, bank, code, enabled, added_at) " +
                    "VALUES ('hdfc-upi', 'HDFC', 'khata1:e30', 1, 1790000000000)"
            )
        }

        helper.runMigrationsAndValidate(dbName, 4, true, *KhataMigrations.ALL).use { db ->
            db.query("SELECT body FROM unparsed_sms").use {
                it.moveToFirst()
                assertEquals("Rs.450 spent on card", it.getString(0))
            }
            db.query("SELECT rule_id FROM custom_parsers").use {
                it.moveToFirst()
                assertEquals("hdfc-upi", it.getString(0))
            }
            db.execSQL(
                "INSERT INTO ignore_rules (kind, header, pattern, sample, enabled, created_at) " +
                    "VALUES ('sender', 'HDFCBK', NULL, 'Rs.450 spent on card', 1, 1790000000000)"
            )
            db.query("SELECT kind, header, pattern FROM ignore_rules").use {
                it.moveToFirst()
                assertEquals("sender", it.getString(0))
                assertEquals("HDFCBK", it.getString(1))
                assertTrue(it.isNull(2))
            }
        }
    }
}
