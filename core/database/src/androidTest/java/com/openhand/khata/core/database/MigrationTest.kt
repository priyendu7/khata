package com.openhand.khata.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration test harness. Uses the exported schemas in `core/database/schemas/` and the same
 * [KhataMigrations.ALL] list as the app. For each new version N, add a test that creates the
 * database at N-1, fills it with data, runs [MigrationTestHelper.runMigrationsAndValidate] to N,
 * and checks the data survived.
 *
 * Runs on plain SQLite: SQLCipher encrypts pages but doesn't change the schema or SQL.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            KhataDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory()
        )

    @Test
    fun version1SchemaMatchesTheExportedSchema() {
        helper.createDatabase(dbName, 1).close()
        helper.runMigrationsAndValidate(dbName, 1, true, *KhataMigrations.ALL).close()
    }

    @Test
    fun version6To7AddsEventsAndKeepsTagsAndTransactions() {
        helper.createDatabase(dbName, 6).use { db ->
            db.execSQL("INSERT INTO tags (id, name) VALUES (1, 'Goa trip')")
            db.execSQL(
                "INSERT INTO categories (id, name, color, icon, archived) " +
                    "VALUES (1, 'Food', 0, 'food', 0)"
            )
            db.execSQL(
                "INSERT INTO transactions (id, amount_paise, direction, timestamp, " +
                    "category_id, source, needs_review) " +
                    "VALUES (1, 10000, 'debit', 1790000000000, 1, 'manual', 0)"
            )
            db.execSQL("INSERT INTO transaction_tags (transaction_id, tag_id) VALUES (1, 1)")
        }

        helper.runMigrationsAndValidate(dbName, 7, true, *KhataMigrations.ALL).use { db ->
            db.execSQL("INSERT INTO events (tag_id, start_day, end_day) VALUES (1, 20728, 20730)")
            db.query("SELECT COUNT(*) FROM transaction_tags").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            // Deleting the tag deletes its event.
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("DELETE FROM tags WHERE id = 1")
            db.query("SELECT COUNT(*) FROM events").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun oldestSchemaOpensWithAllMigrationsAtTheCurrentVersion() {
        helper.createDatabase(dbName, 1).close()

        Room
            .databaseBuilder(
                InstrumentationRegistry.getInstrumentation().targetContext,
                KhataDatabase::class.java,
                dbName
            )
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .addMigrations(*KhataMigrations.ALL)
            .build()
            .apply { openHelper.writableDatabase }
            .close()
    }
}
