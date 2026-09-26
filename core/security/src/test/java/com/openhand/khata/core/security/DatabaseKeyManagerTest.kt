package com.openhand.khata.core.security

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatabaseKeyManagerTest {
    @get:Rule val folder = TemporaryFolder()

    /** Reversible stand-in for the Keystore; "losing the key" makes every unwrap fail. */
    private class FakeKeyWrapper : KeyWrapper {
        var keyLost = false
        var deleted = 0

        override fun wrap(plaintext: ByteArray) =
            byteArrayOf(0x42) + plaintext.map { (it.toInt() xor 0x5A).toByte() }

        override fun unwrap(wrapped: ByteArray): ByteArray {
            if (keyLost ||
                wrapped.firstOrNull() != 0x42.toByte()
            ) {
                throw KeyUnavailableException("lost")
            }
            return wrapped.drop(1).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        }

        override fun deleteKey() {
            deleted++
            keyLost = false
        }
    }

    private val wrapper = FakeKeyWrapper()

    private fun manager(file: File = File(folder.root, "key.bin")) =
        DatabaseKeyManager(file, wrapper)

    @Test
    fun firstUseCreatesAPassphraseAndStoresOnlyTheWrappedForm() {
        val file = File(folder.root, "key.bin")
        val passphrase = manager(file).getOrCreatePassphrase()

        assertTrue(passphrase.created)
        assertFalse(passphrase.previousKeyLost)
        assertEquals(DatabaseKeyManager.PASSPHRASE_BYTES, passphrase.bytes.size)
        assertFalse(file.readBytes().contentEquals(passphrase.bytes))
        assertFalse(File(file.path + ".tmp").exists())
    }

    @Test
    fun laterUseReturnsTheSamePassphrase() {
        val first = manager().getOrCreatePassphrase()
        val second = manager().getOrCreatePassphrase()

        assertFalse(second.created)
        assertArrayEquals(first.bytes, second.bytes)
    }

    @Test
    fun lostKeystoreKeyCreatesANewPassphraseAndSaysSo() {
        val first = manager().getOrCreatePassphrase()
        wrapper.keyLost = true

        val second = manager().getOrCreatePassphrase()

        assertTrue(second.created)
        assertTrue(second.previousKeyLost)
        assertEquals(1, wrapper.deleted)
        assertFalse(first.bytes.contentEquals(second.bytes))
        assertArrayEquals(second.bytes, manager().getOrCreatePassphrase().bytes)
    }

    @Test
    fun damagedKeyFileIsTreatedAsALostKey() {
        val file = File(folder.root, "key.bin")
        manager(file).getOrCreatePassphrase()
        file.writeBytes(byteArrayOf(1, 2, 3))

        assertTrue(manager(file).getOrCreatePassphrase().previousKeyLost)
    }
}
