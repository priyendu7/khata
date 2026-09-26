package com.openhand.khata.core.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreKeyWrapperTest {
    private val wrapper = AndroidKeystoreKeyWrapper(alias = "khata_test_${System.nanoTime()}")
    private val secret = ByteArray(32) { (it * 7).toByte() }

    @After
    fun tearDown() = wrapper.deleteKey()

    @Test
    fun wrapsAndUnwraps() {
        val wrapped = wrapper.wrap(secret)

        assertFalse(wrapped.contentEquals(secret))
        assertArrayEquals(secret, wrapper.unwrap(wrapped))
    }

    @Test(expected = KeyUnavailableException::class)
    fun unwrapFailsOnceTheKeyIsDeleted() {
        val wrapped = wrapper.wrap(secret)
        wrapper.deleteKey()

        wrapper.unwrap(wrapped)
    }

    @Test(expected = KeyUnavailableException::class)
    fun unwrapFailsWhenTheCiphertextIsTampered() {
        val wrapped = wrapper.wrap(secret)
        wrapped[wrapped.lastIndex] = (wrapped.last().toInt() xor 1).toByte()

        wrapper.unwrap(wrapped)
    }
}
