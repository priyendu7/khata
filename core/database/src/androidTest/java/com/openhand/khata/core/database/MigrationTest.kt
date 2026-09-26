package com.openhand.khata.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
