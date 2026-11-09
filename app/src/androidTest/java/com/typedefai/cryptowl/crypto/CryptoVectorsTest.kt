package com.typedefai.cryptowl.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Golden vectors pinned from the desktop reference
 * (`cryptowl-desktop/tests/core/tst_crypto_vectors.cpp`). Any crypto change
 * that breaks these vectors breaks cross-platform vault compatibility.
 *
 * Fixture: device secret `0x11 × 32`, password `correct horse battery staple`,
 * argon2 salt `0x22 × 32`, hkdf salt `0x33 × 32`, VaultKey `0x44 × 32`,
 * nonce `0x55 × 12`, vault id `personal`.
 */
@RunWith(AndroidJUnit4::class)
class CryptoVectorsTest {

    private val kdf = KdfService()

    @Test
    fun golden_vectors_match_desktop() {
        val deviceSecret = ProtectedValue.fromBinary(ByteArray(32) { 0x11 })
        val password = ProtectedValue.fromString(PASSWORD)
        val argon2Salt = ByteArray(32) { 0x22 }
        val hkdfSalt = ByteArray(32) { 0x33 }
        val vaultKey = ProtectedValue.fromBinary(ByteArray(32) { 0x44 })
        try {
            val p = deviceSecret.use { secret ->
                password.use { pw -> HmacSha256.mac(secret, pw) }
            }
            assertEquals(VECTOR_P, p.toHex())
            p.fill(0)

            val tmk = kdf.createTransformedMasterKey(password, deviceSecret, argon2Salt)
            try {
                assertEquals(VECTOR_TMK, tmk.use { it.toHex() })

                val smk = kdf.createStretchedMasterKey(tmk, "personal".toByteArray(), hkdfSalt)
                try {
                    assertEquals(VECTOR_SMK, smk.use { it.toHex() })

                    val wrapped = kdf.wrapKey(
                        key = vaultKey,
                        wrappingKey = kdf.vaultKey(smk),
                        aad = "vault_key:smk".toByteArray(),
                        nonce = ByteArray(12) { 0x55 },
                    )
                    assertEquals(VECTOR_GCM_CIPHERTEXT, wrapped.cipherText.toHex())
                    assertEquals(VECTOR_GCM_TAG, wrapped.authTag.toHex())
                } finally {
                    smk.clear()
                }
            } finally {
                tmk.clear()
            }

            assertEquals(VECTOR_FEK, kdf.fileKey(vaultKey).use { it.toHex() })
            assertEquals(VECTOR_HFK, kdf.fingerprintKey(vaultKey).use { it.toHex() })
        } finally {
            deviceSecret.clear()
            password.clear()
            vaultKey.clear()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val PASSWORD = "correct horse battery staple"

        const val VECTOR_P =
            "89a89e43cc75104a75d4dbcc60e7e6ed0d308e718c0f7b1bf05efaccd70c030b"
        const val VECTOR_TMK =
            "9049f8f4d35de3aef703a68d656e7b777f9e1742d89455414a8fd118c3043588"
        const val VECTOR_SMK =
            "551afd2b0857e902118857a01a680b14e43ff2172950a71a5ec8ec147fcbc809" +
                "0471ff96c2b5486960b6596cfe6cfb7b412b93034a7950badda783f298b2e06f"
        const val VECTOR_GCM_CIPHERTEXT =
            "95a6a822d26244284a3b0f4b0f525d433dcd13ab523fe7da64b8dfb95849f045"
        const val VECTOR_GCM_TAG = "f4e47f45e19f97f44e5feb86379dad10"
        const val VECTOR_FEK =
            "467b0ef6d6792bec4b6fd16a08ed26b4a769cb69dfc91dd8d386ee5640f3f724"
        const val VECTOR_HFK =
            "e18b8c8bd06eb2070ef02238f931908fc241da4fbb14f984c8027ac078538b7f"
    }
}
