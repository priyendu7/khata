package com.openhand.khata.core.database

import android.content.Context
import androidx.room.Room
import com.openhand.khata.core.security.DatabaseKeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * The opened database.
 *
 * @property wasReset true when an existing database couldn't be decrypted (its key was lost, e.g.
 *   after the phone's secure storage was reset) and was replaced with an empty one. The UI should
 *   tell the user, and point them to CSV backups.
 */
class OpenedDatabase(val database: KhataDatabase, val wasReset: Boolean)

/** Opens [KhataDatabase] encrypted with SQLCipher (PRD privacy principle 3). */
object KhataDatabaseFactory {
    @Volatile private var nativeLibraryLoaded = false

    fun open(
        context: Context,
        keyManager: DatabaseKeyManager,
        name: String = KhataDatabase.NAME
    ): OpenedDatabase {
        loadNativeLibrary()
        val passphrase = keyManager.getOrCreatePassphrase()

        // A new passphrase means any existing file was encrypted with a key that no longer exists:
        // it can never be opened again, so start fresh instead of crashing on every launch.
        // (If the key is fine but the file won't open, nothing is deleted and the error surfaces.)
        val wasReset = passphrase.created && context.getDatabasePath(name).exists()
        if (wasReset) context.deleteDatabase(name)

        val database =
            Room
                .databaseBuilder(context.applicationContext, KhataDatabase::class.java, name)
                // Clears its copy of the passphrase from memory once the database is open.
                .openHelperFactory(SupportOpenHelperFactory(passphrase.bytes))
                .addMigrations(*KhataMigrations.ALL)
                .addCallback(DefaultCategorySeeder.callback)
                .build()
        return OpenedDatabase(database, wasReset)
    }

    private fun loadNativeLibrary() {
        if (!nativeLibraryLoaded) {
            synchronized(this) {
                if (!nativeLibraryLoaded) {
                    System.loadLibrary("sqlcipher")
                    nativeLibraryLoaded = true
                }
            }
        }
    }
}
