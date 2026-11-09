package com.typedefai.cryptowl.crypto

import java.nio.charset.StandardCharsets
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AesGcmTest {

    private val data = "hello world!".toByteArray(StandardCharsets.UTF_8)
    private val key = "3f09ea13ceffb8e867a4af3ab17854f9f5f152591653c737a8962b94356e2c0f".hexToBytes()
    private val nonce = "b27f6e2bd596308c190c4f1d".hexToBytes()
    private val aad = "41964e60-5fc3-472c-8b87-71363c71b03c".toByteArray()

    @Test
    fun `encrypts with expected ciphertext and auth tag`() {
        val encrypted = AesGcm.encrypt(key, nonce, aad, data)
        assertEquals("33335861071ff401989294fa", encrypted.cipherText.toHex())
        assertEquals("53b19b6a4498a61b415c2e7963f1cab5", encrypted.authTag.toHex())
    }

    @Test
    fun `decrypts given correct key nonce and aad`() {
        val encrypted = AuthEncryptedData(
            cipherText = "33335861071ff401989294fa".hexToBytes(),
            authTag = "53b19b6a4498a61b415c2e7963f1cab5".hexToBytes(),
        )
        val decrypted = AesGcm.decrypt(key, nonce, aad, encrypted)
        assertArrayEquals(data, decrypted)
    }

    @Test
    fun `round trips`() {
        val encrypted = AesGcm.encrypt(key, nonce, aad, data)
        val decrypted = AesGcm.decrypt(key, nonce, aad, encrypted)
        assertArrayEquals(data, decrypted)
    }

    @Test(expected = AEADBadTagException::class)
    fun `throws when ciphertext is tampered`() {
        val encrypted = AuthEncryptedData(
            cipherText = "33335861071ff401989294fa".replace('3', '4').hexToBytes(),
            authTag = "53b19b6a4498a61b415c2e7963f1cab5".hexToBytes(),
        )
        AesGcm.decrypt(key, nonce, aad, encrypted)
    }

    @Test(expected = AEADBadTagException::class)
    fun `throws when aad is not provided`() {
        val encrypted = AuthEncryptedData(
            cipherText = "33335861071ff401989294fa".hexToBytes(),
            authTag = "53b19b6a4498a61b415c2e7963f1cab5".hexToBytes(),
        )
        AesGcm.decrypt(key, nonce, ByteArray(0), encrypted)
    }

    @Test(expected = AEADBadTagException::class)
    fun `throws when key is incorrect`() {
        val wrongKey = "3f09ea13ceffb8e867a4af3ab17854f9f5f152591653c737a8962b94356e2c0f".replace('1', '2').hexToBytes()
        val encrypted = AuthEncryptedData(
            cipherText = "33335861071ff401989294fa".hexToBytes(),
            authTag = "53b19b6a4498a61b415c2e7963f1cab5".hexToBytes(),
        )
        AesGcm.decrypt(wrongKey, nonce, aad, encrypted)
    }

    @Test(expected = AEADBadTagException::class)
    fun `throws when nonce is incorrect`() {
        val wrongNonce = "b27f6e2bd596308c190c4f1d".replace('b', 'f').hexToBytes()
        val encrypted = AuthEncryptedData(
            cipherText = "33335861071ff401989294fa".hexToBytes(),
            authTag = "53b19b6a4498a61b415c2e7963f1cab5".hexToBytes(),
        )
        AesGcm.decrypt(key, wrongNonce, aad, encrypted)
    }
}
