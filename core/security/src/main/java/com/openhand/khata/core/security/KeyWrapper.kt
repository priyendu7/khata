package com.openhand.khata.core.security

/**
 * Encrypts ("wraps") small secrets with a key that never leaves secure hardware.
 * The production implementation is [AndroidKeystoreKeyWrapper]; tests use a fake.
 */
interface KeyWrapper {
    fun wrap(plaintext: ByteArray): ByteArray

    /** @throws KeyUnavailableException if the wrapping key is gone or [wrapped] can't be decrypted. */
    fun unwrap(wrapped: ByteArray): ByteArray

    fun deleteKey()
}

/** The wrapped secret can never be recovered: the Keystore key is gone, or the data is damaged. */
class KeyUnavailableException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
