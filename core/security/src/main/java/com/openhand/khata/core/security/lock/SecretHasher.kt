package com.openhand.khata.core.security.lock

import com.openhand.khata.core.security.Pbkdf2
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

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
        if (parts.size != PARTS || parts[0] != PREFIX) return false
        val (roundsText, salt, expected) = parts.drop(1)
        val rounds = roundsText.toIntOrNull()
        // Constant-time comparison.
        return rounds != null &&
            MessageDigest.isEqual(decode(expected), derive(secret, decode(salt), rounds))
    }

    private fun derive(secret: String, salt: ByteArray, rounds: Int): ByteArray =
        Pbkdf2.derive(secret.toCharArray(), salt, rounds, HASH_BITS)

    private fun encode(bytes: ByteArray) =
        Base64.getEncoder().withoutPadding().encodeToString(bytes)

    private fun decode(text: String) = Base64.getDecoder().decode(text)

    companion object {
        const val DEFAULT_ITERATIONS = 120_000
        private const val PREFIX = "pbkdf2-sha256"
        private const val PARTS = 4
        private const val SALT_BYTES = 16
        private const val HASH_BITS = 256
    }
}
