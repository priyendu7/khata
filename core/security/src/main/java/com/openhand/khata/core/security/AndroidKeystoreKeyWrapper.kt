package com.openhand.khata.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps secrets with an AES-256-GCM key held in the Android Keystore. The key material can't be
 * exported from the device, and it isn't tied to user authentication, so background work
 * (e.g. SMS import in Phase 3) can open the database too.
 */
class AndroidKeystoreKeyWrapper(private val alias: String = DEFAULT_ALIAS) : KeyWrapper {
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override fun wrap(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: createKey())
        return WrappedKeyFormat.encode(cipher.iv, cipher.doFinal(plaintext))
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        val key = existingKey() ?: throw KeyUnavailableException("Keystore key '$alias' is missing")
        val (iv, ciphertext) = WrappedKeyFormat.decode(wrapped)
        return try {
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                doFinal(ciphertext)
            }
        } catch (e: GeneralSecurityException) {
            // Bad tag, invalidated key, etc.: the wrapped secret can't be recovered.
            throw KeyUnavailableException("Keystore key '$alias' can't decrypt the wrapped key", e)
        }
    }

    override fun deleteKey() {
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    // A key that can't be read is treated as missing; the caller then creates a new passphrase.
    @Suppress("SwallowedException")
    private fun existingKey(): SecretKey? = try {
        keyStore.getKey(alias, null) as? SecretKey
    } catch (e: GeneralSecurityException) {
        null
    }

    private fun createKey(): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec
                    .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    .build()
            )
            generateKey()
        }

    companion object {
        const val DEFAULT_ALIAS = "khata_database_key_wrapper"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val KEY_BITS = 256
    }
}
