package com.typedefai.cryptowl.vault

import com.typedefai.cryptowl.crypto.AesGcm
import com.typedefai.cryptowl.crypto.AuthEncryptedData
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.crypto.RandomUtil

/**
 * Whole-file envelope used for embedded `t_file.content` rows
 * (docs/items.md "File storage: embedded by default"):
 *
 *   envelope = nonce(12) || ciphertext || tag(16), AES-256-GCM,
 *   AAD = file id (C: FEK; S/T: the item's DEK).
 *
 * This is *not* the CWO1 format — embedded files carry no 22-byte header.
 * Thumbnails use the same envelope with AAD `"<file_id>:thumb"`.
 */
object FileEnvelope {

    fun encryptEmbedded(key: ProtectedValue, aad: ByteArray, plaintext: ByteArray): ByteArray {
        val nonce = RandomUtil.generateSecureBytes(AesGcm.NONCE_SIZE)
        val encrypted = key.use { raw -> AesGcm.encrypt(raw, nonce, aad, plaintext) }
        return nonce + encrypted.cipherText + encrypted.authTag
    }

    fun decryptEmbedded(key: ProtectedValue, aad: ByteArray, envelope: ByteArray): ByteArray {
        require(envelope.size >= AesGcm.NONCE_SIZE + AesGcm.TAG_SIZE_BYTES) {
            "embedded file envelope is truncated"
        }
        val nonce = envelope.copyOfRange(0, AesGcm.NONCE_SIZE)
        val tag = envelope.copyOfRange(envelope.size - AesGcm.TAG_SIZE_BYTES, envelope.size)
        val cipherText = envelope.copyOfRange(
            AesGcm.NONCE_SIZE,
            envelope.size - AesGcm.TAG_SIZE_BYTES,
        )
        return key.use { raw ->
            AesGcm.decrypt(raw, nonce, aad, AuthEncryptedData(cipherText, tag))
        }
    }
}
