package com.openhand.khata.core.data

import com.openhand.khata.core.security.Pbkdf2
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The settings file's envelope (`docs/settings-format.md`): a key derived from the user's password
 * with PBKDF2-HMAC-SHA256, then AES-256-GCM, with the header as associated data so changing any
 * byte of it is caught. A fresh salt and IV for every file. Only `javax.crypto`, so no new
 * dependency. Slow on purpose: call off the main thread.
 */
class SettingsCrypto(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val random: SecureRandom = SecureRandom()
) {
    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC)
            .put(FORMAT_VERSION)
            .put(KDF_PBKDF2_SHA256)
            .putInt(iterations)
            .put(salt)
            .put(iv)
            .array()
        val cipher = cipher(Cipher.ENCRYPT_MODE, password, salt, iterations, iv)
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain)
    }

    /** @throws SettingsFileException if this isn't a settings file, or it can't be opened. */
    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        headerError(file)?.let { throw SettingsFileException(it) }
        val rounds = ByteBuffer.wrap(file).getInt(ROUNDS_AT)
        val salt = file.copyOfRange(SALT_AT, SALT_AT + SALT_BYTES)
        val iv = file.copyOfRange(IV_AT, IV_AT + IV_BYTES)
        return try {
            val cipher = cipher(Cipher.DECRYPT_MODE, password, salt, rounds, iv)
            cipher.updateAAD(file, 0, HEADER_BYTES)
            cipher.doFinal(file, HEADER_BYTES, file.size - HEADER_BYTES)
        } catch (_: GeneralSecurityException) {
            // GCM can't tell a wrong password from a changed byte, and neither should we.
            throw SettingsFileException(SettingsFileError.WRONG_PASSWORD)
        }
    }

    private fun headerError(file: ByteArray): SettingsFileError? {
        val complete = file.size >= HEADER_BYTES + TAG_BYTES
        return when {
            file.size < MAGIC.size || !file.copyOf(MAGIC.size).contentEquals(MAGIC) ->
                SettingsFileError.NOT_SETTINGS_FILE
            file.size > VERSION_AT && file[VERSION_AT] > FORMAT_VERSION ->
                SettingsFileError.NEWER_VERSION
            !complete ||
                file[VERSION_AT] != FORMAT_VERSION ||
                file[KDF_AT] != KDF_PBKDF2_SHA256 ||
                ByteBuffer.wrap(file).getInt(ROUNDS_AT) !in 1..MAX_ITERATIONS ->
                SettingsFileError.DAMAGED
            else -> null
        }
    }

    private fun cipher(
        mode: Int,
        password: CharArray,
        salt: ByteArray,
        rounds: Int,
        iv: ByteArray
    ): Cipher {
        val key = Pbkdf2.derive(password, salt, rounds, KEY_BITS)
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            key.fill(0)
        }
    }

    companion object {
        /** OWASP's 2023 figure for PBKDF2-HMAC-SHA256: about a second on a mid-range phone. */
        const val DEFAULT_ITERATIONS = 600_000

        /** A file asking for more would hang the app; no Khata ever writes one. */
        private const val MAX_ITERATIONS = 10_000_000
        private val MAGIC = "KHATASET".toByteArray(Charsets.US_ASCII)
        private const val FORMAT_VERSION: Byte = 1
        private const val KDF_PBKDF2_SHA256: Byte = 1
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val INT_BYTES = 4

        // The header: magic, format version, KDF, iterations, salt, IV.
        private const val VERSION_AT = 8
        private const val KDF_AT = VERSION_AT + 1
        private const val ROUNDS_AT = KDF_AT + 1
        private const val SALT_AT = ROUNDS_AT + INT_BYTES
        private const val IV_AT = SALT_AT + SALT_BYTES
        private const val HEADER_BYTES = IV_AT + IV_BYTES
        private const val KEY_BITS = 256
        private const val TAG_BITS = 128
        private const val TAG_BYTES = TAG_BITS / 8
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

enum class SettingsFileError {
    /** Not a Khata settings file at all. */
    NOT_SETTINGS_FILE,

    /** Made by a newer Khata, in a format this one doesn't know. */
    NEWER_VERSION,

    /** The password is wrong, or the file was changed or cut short. */
    WRONG_PASSWORD,

    /** A settings file whose header or contents make no sense. */
    DAMAGED
}

class SettingsFileException(val error: SettingsFileError) : Exception(error.name)
