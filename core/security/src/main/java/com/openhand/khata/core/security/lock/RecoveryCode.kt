package com.openhand.khata.core.security.lock

import java.security.SecureRandom

/**
 * One-time recovery codes for a forgotten app PIN: 16 characters (about 78 bits) from an alphabet
 * without look-alikes (no 0/O, 1/I/L, U), shown as `XXXX-XXXX-XXXX-XXXX`.
 */
object RecoveryCode {
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ"
    const val LENGTH = 16
    const val GROUP_SIZE = 4
    const val PLACEHOLDER = "XXXX-XXXX-XXXX-XXXX"

    fun generate(random: SecureRandom = SecureRandom()): String =
        String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })

    /** Groups of four, for display. */
    fun format(code: String): String = code.chunked(GROUP_SIZE).joinToString("-")

    /** What the user typed, reduced to the code's characters (case, spaces and dashes ignored). */
    fun normalize(input: String): String = input.uppercase().filter { it in ALPHABET }
}
