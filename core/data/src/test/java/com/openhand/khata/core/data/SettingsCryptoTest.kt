package com.openhand.khata.core.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCryptoTest {
    // Few iterations keep the tests fast; the format stores the count, so it still round-trips.
    private val crypto = SettingsCrypto(iterations = 1_000)
    private val plain = """{"formatVersion":1}""".toByteArray()
    private val password = "correct horse".toCharArray()

    private fun error(block: () -> Unit): SettingsFileError? = try {
        block()
        null
    } catch (e: SettingsFileException) {
        e.error
    }

    @Test
    fun roundTrip() {
        val file = crypto.encrypt(plain, password)

        assertArrayEquals(plain, crypto.decrypt(file, password))
        assertTrue(String(file.copyOf(8), Charsets.US_ASCII) == "KHATASET")
    }

    @Test
    fun aWrongPasswordFails() {
        val file = crypto.encrypt(plain, password)

        assertEquals(
            SettingsFileError.WRONG_PASSWORD,
            error { crypto.decrypt(file, "wrong password".toCharArray()) }
        )
    }

    @Test
    fun aChangedByteInTheHeaderFails() {
        val file = crypto.encrypt(plain, password)
        // A byte of the salt: the header is checked as associated data.
        file[HEADER_SALT_BYTE] = (file[HEADER_SALT_BYTE] + 1).toByte()

        assertEquals(SettingsFileError.WRONG_PASSWORD, error { crypto.decrypt(file, password) })
    }

    @Test
    fun aChangedIterationCountFails() {
        val file = crypto.encrypt(plain, password)
        file[ITERATIONS_LOW_BYTE] = (file[ITERATIONS_LOW_BYTE] + 1).toByte()

        assertEquals(SettingsFileError.WRONG_PASSWORD, error { crypto.decrypt(file, password) })
    }

    @Test
    fun aChangedByteInTheCiphertextFails() {
        val file = crypto.encrypt(plain, password)
        file[file.size - 1] = (file[file.size - 1] + 1).toByte()

        assertEquals(SettingsFileError.WRONG_PASSWORD, error { crypto.decrypt(file, password) })
    }

    @Test
    fun aFileCutShortFails() {
        val file = crypto.encrypt(plain, password)

        assertEquals(
            SettingsFileError.WRONG_PASSWORD,
            error { crypto.decrypt(file.copyOf(file.size - 1), password) }
        )
        assertEquals(SettingsFileError.DAMAGED, error { crypto.decrypt(file.copyOf(20), password) })
    }

    @Test
    fun twoExportsOfTheSameDataDiffer() {
        val first = crypto.encrypt(plain, password)
        val second = crypto.encrypt(plain, password)

        assertFalse(first.contentEquals(second))
        assertFalse(
            first.copyOfRange(SALT_AT, SALT_AT + 16).contentEquals(
                second.copyOfRange(
                    SALT_AT,
                    SALT_AT + 16
                )
            )
        )
        assertFalse(
            first.copyOfRange(IV_AT, IV_AT + 12).contentEquals(
                second.copyOfRange(
                    IV_AT,
                    IV_AT + 12
                )
            )
        )
    }

    @Test
    fun aCsvFileIsNotASettingsFile() {
        assertEquals(
            SettingsFileError.NOT_SETTINGS_FILE,
            error { crypto.decrypt("date,amount\n".toByteArray(), password) }
        )
    }

    @Test
    fun aNewerFormatIsRefused() {
        val file = crypto.encrypt(plain, password)
        file[VERSION_AT] = 2

        assertEquals(SettingsFileError.NEWER_VERSION, error { crypto.decrypt(file, password) })
    }

    private companion object {
        const val VERSION_AT = 8
        const val ITERATIONS_LOW_BYTE = 13
        const val SALT_AT = 14
        const val HEADER_SALT_BYTE = SALT_AT + 3
        const val IV_AT = 30
    }
}
