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

    @Test
    fun version4To5AddsOwnAccountAndLetsBothSidesOfATransferShareAReference() {
        helper.createDatabase(dbName, 4).use { db ->
            db.execSQL(
                "INSERT INTO categories (id, name, seed_key, color, icon, archived) " +
                    "VALUES (1, NULL, 'uncategorized', 0, 'category', 0)"
            )
            db.execSQL(
                "INSERT INTO payees (id, identifier, display_name, default_category_id) " +
                    "VALUES (3, 'CRED', 'CRED', NULL)"
            )
            db.execSQL(
                "INSERT INTO transactions (id, amount_paise, direction, timestamp, category_id, " +
                    "payee_id, reference_no, source, needs_review) " +
                    "VALUES (7, 36600, 'debit', 1790000000000, 1, 3, '4087', 'sms', 0)"
            )
        }

        helper.runMigrationsAndValidate(dbName, 5, true, *KhataMigrations.ALL).use { db ->
            db.query("SELECT own_account FROM payees WHERE id = 3").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
            db.execSQL(
                "INSERT INTO transactions (id, amount_paise, direction, timestamp, category_id, " +
                    "reference_no, source, needs_review) " +
                    "VALUES (8, 36600, 'credit', 1790000000000, 1, '4087', 'sms', 0)"
            )
            db.query("SELECT COUNT(*) FROM transactions WHERE reference_no = '4087'").use {
                it.moveToFirst()
                assertEquals(2, it.getInt(0))
            }
        }
    }

    @Test
    fun version5To6AddsCountsAtAndLeavesExistingRowsSameAsDate() {
        helper.createDatabase(dbName, 5).use { db ->
            db.execSQL(
                "INSERT INTO categories (id, name, seed_key, color, icon, archived) " +
                    "VALUES (1, NULL, 'uncategorized', 0, 'category', 0)"
            )
            db.execSQL(
                "INSERT INTO transactions (id, amount_paise, direction, timestamp, category_id, " +
                    "source, needs_review) VALUES (7, 8500000, 'credit', 1790000000000, 1, " +
                    "'manual', 0)"
            )
        }

        helper.runMigrationsAndValidate(dbName, 6, true, *KhataMigrations.ALL).use { db ->
            db.query("SELECT amount_paise, counts_at FROM transactions WHERE id = 7").use {
                it.moveToFirst()
                assertEquals(8500000L, it.getLong(0))
                assertTrue(it.isNull(1))
            }
        }
    }

    @Test
    fun version7To8AddsBuiltInRuleOverridesAndKeepsCustomParsers() {
        helper.createDatabase(dbName, 7).use { db ->
            db.execSQL(
                "INSERT INTO custom_parsers (rule_id, bank, code, enabled, added_at) " +
                    "VALUES ('hdfc-upi', 'HDFC', 'khata1:e30', 0, 1790000000000)"
            )
        }

        helper.runMigrationsAndValidate(dbName, 8, true, *KhataMigrations.ALL).use { db ->
            db.query("SELECT rule_id, enabled FROM custom_parsers").use {
                it.moveToFirst()
                assertEquals("hdfc-upi", it.getString(0))
                assertEquals(0, it.getInt(1))
            }
            db.execSQL("INSERT INTO builtin_rule_overrides (rule_id) VALUES ('kotak-upi-sent')")
            db.query("SELECT enabled, edited_code, base_hash FROM builtin_rule_overrides").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
                assertTrue(it.isNull(1))
                assertTrue(it.isNull(2))
            }
        }
    }

    @Test
    fun version8To9AddsTransferDetailsAndKeepsTransfers() {
        helper.createDatabase(dbName, 8).use { db ->
            db.execSQL(
                "INSERT INTO categories (id, name, seed_key, color, icon, archived) " +
                    "VALUES (1, NULL, 'uncategorized', 0, 'category', 0)"
            )
            db.execSQL(
                "INSERT INTO transactions (id, amount_paise, direction, timestamp, category_id, " +
                    "source, raw_sms, needs_review) VALUES (7, 500000, 'transfer', " +
                    "1790000000000, 1, 'sms', 'Sent Rs 5000 to CRED', 0)"
            )
        }

        helper.runMigrationsAndValidate(dbName, 9, true, *KhataMigrations.ALL).use { db ->
            db.query(
                "SELECT amount_paise, direction, raw_sms, transfer_side, transfer_pair_id, " +
                    "transfer_kind FROM transactions WHERE id = 7"
            ).use {
                it.moveToFirst()
                assertEquals(500000L, it.getLong(0))
                assertEquals("transfer", it.getString(1))
                assertEquals("Sent Rs 5000 to CRED", it.getString(2))
                // Filled in later by the back-fill, from the SMS.
                assertTrue(it.isNull(3))
                assertTrue(it.isNull(4))
                assertTrue(it.isNull(5))
            }
        }
    }
}
