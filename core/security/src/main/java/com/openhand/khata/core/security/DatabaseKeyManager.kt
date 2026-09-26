package com.openhand.khata.core.security

import android.content.Context
import java.io.File
import java.security.SecureRandom

/**
 * The database passphrase.
 *
 * @property created true when no usable passphrase existed and a new one was generated, so any
 *   existing database file was encrypted with a different passphrase and can't be opened.
 * @property previousKeyLost true when a wrapped passphrase existed but can no longer be decrypted
 *   (e.g. the Keystore was reset). Always implies [created].
 */
class DatabasePassphrase(val bytes: ByteArray, val created: Boolean, val previousKeyLost: Boolean)

/**
 * Creates the SQLCipher passphrase on first use and keeps it wrapped by the Android Keystore.
 * Only the wrapped form is written to disk (in no-backup storage); the plain passphrase lives in
 * memory just long enough to open the database, and is never logged.
 */
class DatabaseKeyManager(
    private val wrappedKeyFile: File,
    private val keyWrapper: KeyWrapper,
    private val random: SecureRandom = SecureRandom()
) {
    @Synchronized
    fun getOrCreatePassphrase(): DatabasePassphrase {
        var previousKeyLost = false
        if (wrappedKeyFile.exists()) {
            try {
                val bytes = keyWrapper.unwrap(wrappedKeyFile.readBytes())
                return DatabasePassphrase(bytes, created = false, previousKeyLost = false)
            } catch (e: KeyUnavailableException) {
                previousKeyLost = true
                keyWrapper.deleteKey()
            }
        }
        val bytes = ByteArray(PASSPHRASE_BYTES).also(random::nextBytes)
        writeAtomically(keyWrapper.wrap(bytes))
        return DatabasePassphrase(bytes.copyOf(), created = true, previousKeyLost = previousKeyLost)
    }

    private fun writeAtomically(data: ByteArray) {
        wrappedKeyFile.parentFile?.mkdirs()
        val temp = File(wrappedKeyFile.path + ".tmp")
        temp.writeBytes(data)
        if (!temp.renameTo(wrappedKeyFile)) {
            temp.delete()
            error("Couldn't save the wrapped database key")
        }
    }

    companion object {
        const val PASSPHRASE_BYTES = 32
        private const val WRAPPED_KEY_FILE = "database-key.bin"

        /** Production instance: key file in no-backup storage, wrapped by the Android Keystore. */
        fun create(context: Context): DatabaseKeyManager = DatabaseKeyManager(
            File(context.noBackupFilesDir, WRAPPED_KEY_FILE),
            AndroidKeystoreKeyWrapper()
        )
    }
}
