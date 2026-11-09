package com.typedefai.cryptowl.crypto

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class AuthEncryptedData(
    val cipherText: ByteArray,
    val authTag: ByteArray,
)

/** AES-256-GCM authenticated encryption; throws AEADBadTagException on auth failure. */
object AesGcm {

    const val KEY_SIZE = 32
    const val NONCE_SIZE = 12
    const val TAG_SIZE_BYTES = 16
    private const val TAG_SIZE = 16
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, plainText: ByteArray): AuthEncryptedData {
        require(key.size == KEY_SIZE) { "key must be $KEY_SIZE bytes" }
        require(nonce.size == NONCE_SIZE) { "nonce must be $NONCE_SIZE bytes" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_SIZE * 8, nonce),
        )
        cipher.updateAAD(aad)
        val output = cipher.doFinal(plainText)

        CryptoLog.d(
            C,
            "encrypt key(${CryptoLog.key(key)}) nonce=${nonce.toHexString()} aad='${aad.decodeToString()}' " +
                "plain=${plainText.size}B -> cipher=${output.size - TAG_SIZE}B tag=$TAG_SIZE",
        )
        return AuthEncryptedData(
            cipherText = output.copyOf(output.size - TAG_SIZE),
            authTag = output.copyOfRange(output.size - TAG_SIZE, output.size),
        )
    }

    fun decrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, encrypted: AuthEncryptedData): ByteArray {
        require(key.size == KEY_SIZE) { "key must be $KEY_SIZE bytes" }
        require(nonce.size == NONCE_SIZE) { "nonce must be $NONCE_SIZE bytes" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_SIZE * 8, nonce),
        )
        cipher.updateAAD(aad)
        val combined = encrypted.cipherText + encrypted.authTag
        val plain = try {
            cipher.doFinal(combined)
        } catch (e: Throwable) {
            CryptoLog.e(
                C,
                "decrypt FAILED (bad tag/AAD/nonce): key(${CryptoLog.key(key)}) nonce=${nonce.toHexString()} " +
                    "aad='${aad.decodeToString()}' cipher=${encrypted.cipherText.size}B",
                e,
            )
            throw e
        }
        CryptoLog.d(
            C,
            "decrypt key(${CryptoLog.key(key)}) nonce=${nonce.toHexString()} aad='${aad.decodeToString()}' " +
                "cipher=${encrypted.cipherText.size}B -> plain=${plain.size}B",
        )
        return plain
    }

    private const val C = "AesGcm"
}
