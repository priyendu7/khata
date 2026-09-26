package com.openhand.khata.core.security

/**
 * On-disk layout of a wrapped key: `[format version][IV length][IV][AES-GCM ciphertext + tag]`.
 * Kept separate from the Keystore code so it can be unit-tested on the JVM.
 */
internal object WrappedKeyFormat {
    const val VERSION: Byte = 1
    private const val MAX_IV_BYTES = 255
    private const val BYTE_MASK = 0xFF

    fun encode(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        require(iv.size in 1..MAX_IV_BYTES) { "IV must be 1-$MAX_IV_BYTES bytes" }
        return byteArrayOf(VERSION, iv.size.toByte()) + iv + ciphertext
    }

    /** @return the IV and the ciphertext. @throws KeyUnavailableException if [blob] is malformed. */
    fun decode(blob: ByteArray): Pair<ByteArray, ByteArray> {
        if (blob.size < 2 ||
            blob[0] != VERSION
        ) {
            throw KeyUnavailableException("Unknown wrapped key format")
        }
        val ivLength = blob[1].toInt() and BYTE_MASK
        if (ivLength == 0 ||
            blob.size <= 2 + ivLength
        ) {
            throw KeyUnavailableException("Wrapped key is truncated")
        }
        return blob.copyOfRange(2, 2 + ivLength) to blob.copyOfRange(2 + ivLength, blob.size)
    }
}
