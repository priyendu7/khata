package com.openhand.khata.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openhand.khata.core.security.AndroidKeystoreKeyWrapper
import com.openhand.khata.core.security.DatabaseKeyManager
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val id = System.nanoTime()
    private val dbName = "test-$id.db"
    private val keyFile = File(context.noBackupFilesDir, "test-key-$id.bin")
    private val wrapper = AndroidKeystoreKeyWrapper(alias = "khata_test_db_$id")

    private fun open() =
        KhataDatabaseFactory.open(context, DatabaseKeyManager(keyFile, wrapper), dbName)

    private fun OpenedDatabase.write(value: String) = runBlocking {
        database.metadataDao().put(MetadataEntity("secret", value))
    }

    private fun OpenedDatabase.read() = runBlocking { database.metadataDao().get("secret") }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
        keyFile.delete()
        wrapper.deleteKey()
    }

    @Test
    fun databaseFileIsNotPlainSqlite() {
        open().apply {
            write("hello-khata-plaintext")
            database.close()
        }

        val bytes = context.getDatabasePath(dbName).readBytes()
        val header = String(bytes.copyOfRange(0, 16), Charsets.ISO_8859_1)
        assertFalse("database has a plain SQLite header", header.startsWith("SQLite format 3"))
        assertFalse(
            "value is readable in the file",
            String(bytes, Charsets.ISO_8859_1).contains("hello-khata-plaintext")
        )
    }

    @Test
    fun reopensWithTheSameKeyAndKeepsData() {
        open().apply {
            assertFalse(wasReset)
            write("kept")
            database.close()
        }

        val reopened = open()

        assertFalse(reopened.wasReset)
        assertEquals("kept", reopened.read())
        reopened.database.close()
    }

    @Test
    fun lostKeystoreKeyStartsFreshWithoutCrashing() {
        open().apply {
            write("gone")
            database.close()
        }
        wrapper.deleteKey()

        val reopened = open()

        assertTrue(reopened.wasReset)
        assertNull(reopened.read())
        reopened.database.close()
    }

    @Test
    fun missingKeyFileStartsFreshWithoutCrashing() {
        open().apply {
            write("gone")
            database.close()
        }
        keyFile.delete()

        val reopened = open()

        assertTrue(reopened.wasReset)
        assertNull(reopened.read())
        reopened.database.close()
    }
}
