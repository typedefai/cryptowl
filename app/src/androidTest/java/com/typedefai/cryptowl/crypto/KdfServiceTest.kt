package com.typedefai.cryptowl.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

private fun String.hexToBytes(): ByteArray =
    ByteArray(length / 2) { Integer.parseInt(substring(it * 2, it * 2 + 2), 16).toByte() }

private fun ByteArray.toHex(): String =
    joinToString("") { "%02x".format(it.toInt() and 0xff) }

/**
 * Key-derivation chain tests against the real Argon2 JNI binding.
 * Vectors are the cross-verification contract with the desktop reference
 * (`wechat_sns_export/tests/test_vaultlib.py` — fixed inputs: password
 * "correct horse battery staple", DeviceSecret 0x11*32, argon2Salt 0x22*32,
 * hkdfSalt 0x33*32, vaultId "personal").
 */
@RunWith(AndroidJUnit4::class)
class KdfServiceTest {

    private val service = KdfService()

    private val masterPassword = ProtectedValue.fromString("correct horse battery staple")
    private val deviceSecret = ByteArray(32) { 0x11 }.let { ProtectedValue.fromBinary(it) }
    private val argon2Salt = ByteArray(32) { 0x22 }
    private val hkdfSalt = ByteArray(32) { 0x33 }
    private val vaultId = "personal".toByteArray()

    @Test
    fun derivesTransformedMasterKeyMatchingVaultlibVector() {
        val tmk = service.createTransformedMasterKey(masterPassword, deviceSecret, argon2Salt)

        assertEquals(
            "9049f8f4d35de3aef703a68d656e7b777f9e1742d89455414a8fd118c3043588",
            tmk.binaryValue().toHex(),
        )
    }

    @Test
    fun derivesStretchedMasterKeyMatchingVaultlibVector() {
        val tmk = ProtectedValue.fromBinary(
            "9049f8f4d35de3aef703a68d656e7b777f9e1742d89455414a8fd118c3043588".hexToBytes(),
        )

        val smk = service.createStretchedMasterKey(tmk, vaultId, hkdfSalt)

        assertEquals(
            "551afd2b0857e902118857a01a680b14e43ff2172950a71a5ec8ec147fcbc809" +
                "0471ff96c2b5486960b6596cfe6cfb7b412b93034a7950badda783f298b2e06f",
            smk.binaryValue().toHex(),
        )
    }

    @Test
    fun fekIsHkdfOfVaultKeyWithFileInfo() {
        val fek = service.fileKey(ProtectedValue.fromBinary(ByteArray(32) { 0x44 }))
        val expected = Hkdf.deriveKey(
            ikm = ByteArray(32) { 0x44 },
            salt = ByteArray(0),
            info = "file".toByteArray(),
            outputLength = 32,
        )
        assertArrayEquals(expected, fek.binaryValue())
        assertEquals(32, fek.binaryValue().size)
    }

    @Test
    fun wrapsAndUnwrapsAVaultKeyWithTheDerivedChain() {
        val smk = service.createStretchedMasterKey(
            transformedMasterKey = service.createTransformedMasterKey(masterPassword, deviceSecret, argon2Salt),
            vaultId = vaultId,
            salt = hkdfSalt,
        )
        val wrappingKey = service.vaultKey(smk)
        val vaultKey = ProtectedValue.fromBinary(ByteArray(32) { 0x2a })
        val aad = "vault_key:smk".toByteArray()

        val wrapped = service.wrapKey(vaultKey, wrappingKey, aad)
        val unwrapped = service.unwrapKey(wrapped, wrappingKey, aad)

        assertArrayEquals(vaultKey.binaryValue(), unwrapped.binaryValue())
        assertEquals(WrappedKey.AES_256_GCM, wrapped.algorithm)
        assertEquals(12, wrapped.nonce.size)
        assertEquals(32, wrapped.cipherText.size)
    }

    @Test
    fun unwrapFailsWithWrongPassword() {
        val smk = service.createStretchedMasterKey(
            transformedMasterKey = service.createTransformedMasterKey(masterPassword, deviceSecret, argon2Salt),
            vaultId = vaultId,
            salt = hkdfSalt,
        )
        val wrapped = service.wrapKey(
            key = ProtectedValue.fromBinary(ByteArray(32) { 0x2a }),
            wrappingKey = service.vaultKey(smk),
            aad = "vault_key:smk".toByteArray(),
        )

        val wrongSmk = service.createStretchedMasterKey(
            transformedMasterKey = service.createTransformedMasterKey(
                ProtectedValue.fromString("wrong password"),
                deviceSecret,
                argon2Salt,
            ),
            vaultId = vaultId,
            salt = hkdfSalt,
        )

        try {
            service.unwrapKey(wrapped, service.vaultKey(wrongSmk), "vault_key:smk".toByteArray())
            throw AssertionError("expected AEADBadTagException")
        } catch (expected: AEADBadTagException) {
            // expected
        }
    }
}
