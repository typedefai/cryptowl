package com.typedefai.cryptowl.crypto

import java.nio.charset.StandardCharsets
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Key-derivation chain tests. Vectors cross-verified against cryptowl-ref
 * (Flutter) test cases; the Argon2 step is stubbed here (JNI cannot run in
 * JVM tests) and exercised for real in the instrumented KdfServiceTest.
 */
class KdfServiceTest {

    // --- cryptowl-ref cross-verification constants ---
    private val masterPassword = ProtectedValue.fromString("123456")
    private val deviceSecret =
        "9a54bef1921ce1c89255dc67229ffffd2dd1efb5ef3cdd3da66ae9ab53fb974f".hexToBytes()
            .let { ProtectedValue.fromBinary(it) }
    private val argon2Salt = "b27f6e2bd596308c190c4f1d68660bc3".hexToBytes()

    private val expectedPreHash = "1ccaa6825f04653406e5ecab673141126a5085ab9a36e21690ed41d4ed1d71b7".hexToBytes()
    private val expectedTmk = "509f825b859521f72fe511d2c120f53ed52bf641932d92ba086b89be3d65153a".hexToBytes()
    private val expectedSmk =
        ("6414d3f58fcaf252675f1544e4e7d5e389fd1dd319c6df9693b88b44e7363340" +
            "c38a015a41594d78650f501bd7e86fcd88ba7a21d4efb54dc3820056bd9039c9").hexToBytes()
    private val expectedVaultKey = "6414d3f58fcaf252675f1544e4e7d5e389fd1dd319c6df9693b88b44e7363340".hexToBytes()
    private val expectedMacKey = "c38a015a41594d78650f501bd7e86fcd88ba7a21d4efb54dc3820056bd9039c9".hexToBytes()

    private fun hasherReturning(result: ByteArray, onHash: (ByteArray, ByteArray, KdfParams, Int) -> Unit = { _, _, _, _ -> }): Argon2Hasher =
        Argon2Hasher { password, salt, params, hashLen ->
            onHash(password, salt, params, hashLen)
            result
        }

    // --- TMK chain ---

    @Test
    fun `pre-hashes the master password with the device secret before Argon2`() {
        var captured: ByteArray? = null
        val service = KdfService(hasherReturning(expectedTmk) { password, _, _, _ -> captured = password.copyOf() })

        service.createTransformedMasterKey(masterPassword, deviceSecret, argon2Salt)

        assertArrayEquals(expectedPreHash, captured)
    }

    @Test
    fun `returns the transformed master key matching cryptowl-ref vector`() {
        val service = KdfService(hasherReturning(expectedTmk))

        val tmk = service.createTransformedMasterKey(masterPassword, deviceSecret, argon2Salt)

        assertArrayEquals(expectedTmk, tmk.binaryValue())
    }

    @Test
    fun `uses OWASP argon2id parameters by default`() {
        var captured: KdfParams? = null
        val service = KdfService(hasherReturning(expectedTmk) { _, _, params, _ -> captured = params })

        service.createTransformedMasterKey(masterPassword, deviceSecret, argon2Salt)

        assertEquals("argon2id", captured?.algorithm)
        assertEquals(19456, captured?.mCostKiB)
        assertEquals(2, captured?.tCost)
        assertEquals(1, captured?.parallelism)
    }

    @Test
    fun `honors custom kdf parameters`() {
        var captured: KdfParams? = null
        val service = KdfService(hasherReturning(expectedTmk) { _, _, params, _ -> captured = params })
        val custom = KdfParams(mCostKiB = 65536, tCost = 3, parallelism = 2)

        service.createTransformedMasterKey(masterPassword, deviceSecret, argon2Salt, custom)

        assertEquals(custom, captured)
    }

    // --- SMK chain (pure HKDF, no stub needed) ---

    @Test
    fun `stretches the transformed master key into SMK matching cryptowl-ref vector`() {
        val service = KdfService(hasherReturning(expectedTmk))
        val hkdfSalt = "8a7c01c0b81c8872e016d779486bc189".hexToBytes()
        val vaultId = "WJB6W".toByteArray(StandardCharsets.UTF_8)

        val smk = service.createStretchedMasterKey(
            transformedMasterKey = ProtectedValue.fromBinary(expectedTmk),
            vaultId = vaultId,
            salt = hkdfSalt,
        )

        assertEquals(64, smk.binaryValue().size)
        assertArrayEquals(expectedSmk, smk.binaryValue())
    }

    @Test
    fun `splits SMK into vault key and mac key`() {
        val service = KdfService(hasherReturning(expectedTmk))
        val smk = ProtectedValue.fromBinary(expectedSmk)

        assertArrayEquals(expectedVaultKey, service.vaultKey(smk).binaryValue())
        assertArrayEquals(expectedMacKey, service.macKey(smk).binaryValue())
    }

    // --- Secondary (TS-KEK) chain ---

    @Test
    fun `derives the secondary key with the secondary salt and OWASP params`() {
        val secondarySalt = "3f09ea13ceffb8e867a4af3ab17854f9".hexToBytes()
        val expectedTsKek = "487adc4c030ba4a79773ba6a43d1282c075ba0428547d9b00748ec39df9738b2".hexToBytes()
        var capturedSalt: ByteArray? = null
        var capturedParams: KdfParams? = null
        val service = KdfService(
            hasherReturning(expectedTsKek) { _, salt, params, _ ->
                capturedSalt = salt
                capturedParams = params
            },
        )

        val tsKek = service.createSecondaryKey(ProtectedValue.fromString("secondary-pass"), secondarySalt)

        assertArrayEquals(expectedTsKek, tsKek.binaryValue())
        assertArrayEquals(secondarySalt, capturedSalt)
        assertEquals(KdfParams.OWASP, capturedParams)
    }

    // --- Envelope wrap/unwrap ---

    private val smk = ProtectedValue.fromBinary(expectedSmk)
    private val service = KdfService(hasherReturning(expectedTmk))

    @Test
    fun `wraps a key matching cryptowl-ref vector`() {
        val keyToWrap = ("8fc13f5ef75f029588dfe60f72706283bbc1e781a13f3df799c25131abb8b300" +
            "adf0efe34d377c605f964bd505bf174c1f4521d6244d5e75309dc3ea115b95be").hexToBytes()
        val nonce = "2921075aed8cae8b22aae119".hexToBytes()
        val aad = "WJB6W".toByteArray(StandardCharsets.UTF_8)

        val wrapped = service.wrapKey(
            key = ProtectedValue.fromBinary(keyToWrap),
            wrappingKey = service.vaultKey(smk),
            aad = aad,
            nonce = nonce,
        )

        assertEquals("b2b3cb94378b635340f335529e868a1f87d360cbf0b864be73901eb753d1b5b4320bce63ae386e599114a648422cd3f73321f85ddb4cd89eab936101f3c72883", wrapped.cipherText.toHex())
        assertEquals("4620505d9547ba3e77473bfecbaa5beb", wrapped.authTag.toHex())
        assertEquals(WrappedKey.AES_256_GCM, wrapped.algorithm)
    }

    @Test
    fun `wraps and unwraps a key round trip`() {
        val key = ProtectedValue.fromBinary(ByteArray(32) { 0x2a })
        val wrapped = service.wrapKey(key, service.vaultKey(smk), aad = "vault_key:smk".toByteArray())

        val unwrapped = service.unwrapKey(wrapped, service.vaultKey(smk), "vault_key:smk".toByteArray())

        assertArrayEquals(key.binaryValue(), unwrapped.binaryValue())
    }

    @Test
    fun `generates a fresh nonce per wrap when not provided`() {
        val key = ProtectedValue.fromBinary(ByteArray(32) { 0x2a })
        val aad = "kek:biokey".toByteArray()

        val first = service.wrapKey(key, service.vaultKey(smk), aad)
        val second = service.wrapKey(key, service.vaultKey(smk), aad)

        assertNotEquals(first.nonce.toList(), second.nonce.toList())
    }

    @Test(expected = AEADBadTagException::class)
    fun `throws when ciphertext is tampered`() {
        val wrapped = service.wrapKey(
            ProtectedValue.fromBinary(ByteArray(32) { 0x2a }),
            service.vaultKey(smk),
            "kek:biokey".toByteArray(),
        )
        val tampered = wrapped.copy(cipherText = ByteArray(wrapped.cipherText.size) { 0x00 })

        service.unwrapKey(tampered, service.vaultKey(smk), "kek:biokey".toByteArray())
    }

    @Test(expected = AEADBadTagException::class)
    fun `throws when the wrapping key is wrong`() {
        val wrapped = service.wrapKey(
            ProtectedValue.fromBinary(ByteArray(32) { 0x2a }),
            service.vaultKey(smk),
            "kek:biokey".toByteArray(),
        )

        service.unwrapKey(wrapped, service.macKey(smk), "kek:biokey".toByteArray())
    }

    @Test(expected = AEADBadTagException::class)
    fun `throws when the aad is wrong`() {
        val wrapped = service.wrapKey(
            ProtectedValue.fromBinary(ByteArray(32) { 0x2a }),
            service.vaultKey(smk),
            "kek:biokey".toByteArray(),
        )

        service.unwrapKey(wrapped, service.vaultKey(smk), "kek:other".toByteArray())
    }

    @Test(expected = AEADBadTagException::class)
    fun `throws when the nonce is wrong`() {
        val wrapped = service.wrapKey(
            ProtectedValue.fromBinary(ByteArray(32) { 0x2a }),
            service.vaultKey(smk),
            "kek:biokey".toByteArray(),
        )

        service.unwrapKey(
            wrapped.copy(nonce = ByteArray(12) { 0x11 }),
            service.vaultKey(smk),
            "kek:biokey".toByteArray(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws when the wrapping key is not 32 bytes`() {
        service.wrapKey(
            key = ProtectedValue.fromBinary(ByteArray(32) { 0x2a }),
            wrappingKey = ProtectedValue.fromString("short"),
            aad = ByteArray(0),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws when the nonce is not 12 bytes`() {
        service.wrapKey(
            key = ProtectedValue.fromBinary(ByteArray(32) { 0x2a }),
            wrappingKey = service.vaultKey(smk),
            aad = ByteArray(0),
            nonce = ByteArray(16),
        )
    }
}
