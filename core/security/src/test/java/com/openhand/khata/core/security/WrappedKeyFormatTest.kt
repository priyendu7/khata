package com.openhand.khata.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class WrappedKeyFormatTest {
    @Test
    fun roundTrips() {
        val iv = ByteArray(12) { it.toByte() }
        val ciphertext = ByteArray(48) { (it * 3).toByte() }

        val (decodedIv, decodedCiphertext) = WrappedKeyFormat.decode(
            WrappedKeyFormat.encode(iv, ciphertext)
        )

        assertArrayEquals(iv, decodedIv)
        assertArrayEquals(ciphertext, decodedCiphertext)
    }

    @Test(expected = KeyUnavailableException::class)
    fun rejectsAnUnknownVersion() {
        WrappedKeyFormat.decode(byteArrayOf(9, 12) + ByteArray(40))
    }

    @Test(expected = KeyUnavailableException::class)
    fun rejectsATruncatedBlob() {
        WrappedKeyFormat.decode(byteArrayOf(WrappedKeyFormat.VERSION, 12, 1, 2))
    }
}
