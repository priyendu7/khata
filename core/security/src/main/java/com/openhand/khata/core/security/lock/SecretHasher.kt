package com.openhand.khata.core.security.lock

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted, deliberately slow hashes (PBKDF2-HMAC-SHA256) for the app PIN and recovery code. Only
 * the hash is stored: `pbkdf2-sha256$iterations$salt$hash`. The iteration count is stored with
 * each hash so it can be raised later without breaking existing PINs. Call off the main thread.
 */
class SecretHasher(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val random: SecureRandom = SecureRandom()
) {
    fun hash(secret: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return listOf(
            PREFIX,
            iterations.toString(),
            encode(salt),
            encode(derive(secret, salt, iterations))
        )
            .joinToString("$")
    }

    fun verify(secret: String, stored: String): Boolean {
        val parts = stored.split("$")
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val rounds = parts[1].toIntOrNull() ?: return false
        val expected = decode(parts[3])
        // Constant-time comparison.
        return MessageDigest.isEqual(expected, derive(secret, decode(parts[2]), rounds))
    }

    private fun derive(secret: String, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, rounds, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray) =
        Base64.getEncoder().withoutPadding().encodeToString(bytes)

    private fun decode(text: String) = Base64.getDecoder().decode(text)

    companion object {
        const val DEFAULT_ITERATIONS = 120_000
        private const val PREFIX = "pbkdf2-sha256"
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val SALT_BYTES = 16
        private const val HASH_BITS = 256
    }
}
