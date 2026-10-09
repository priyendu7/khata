package com.openhand.khata.core.security

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-HMAC-SHA256: a slow, salted derivation from a password. Shared by the app PIN hash and
 * the settings file's key, so both use the same well-tested step. Call off the main thread.
 */
object Pbkdf2 {
    const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun derive(secret: CharArray, salt: ByteArray, iterations: Int, bits: Int): ByteArray {
        val spec = PBEKeySpec(secret, salt, iterations, bits)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
