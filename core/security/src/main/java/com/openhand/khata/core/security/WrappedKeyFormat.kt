package com.openhand.khata.core.security

/**
 * On-disk layout of a wrapped key: `[format version][IV length][IV][AES-GCM ciphertext + tag]`.
 * Kept separate from the Keystore code so it can be unit-tested on the JVM.
 */
internal object WrappedKeyFormat {
    const val VERSION: Byte = 1

    fun encode(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        require(iv.size in 1..255) { "IV must be 1-255 bytes" }
        return byteArrayOf(VERSION, iv.size.toByte()) + iv + ciphertext
    }

    /** @return the IV and the ciphertext. @throws KeyUnavailableException if [blob] is malformed. */
    fun decode(blob: ByteArray): Pair<ByteArray, ByteArray> {
        if (blob.size < 2 ||
            blob[0] != VERSION
        ) {
            throw KeyUnavailableException("Unknown wrapped key format")
        }
        val ivLength = blob[1].toInt() and 0xFF
        if (ivLength == 0 ||
            blob.size <= 2 + ivLength
        ) {
            throw KeyUnavailableException("Wrapped key is truncated")
        }
        return blob.copyOfRange(2, 2 + ivLength) to blob.copyOfRange(2 + ivLength, blob.size)
    }
}
