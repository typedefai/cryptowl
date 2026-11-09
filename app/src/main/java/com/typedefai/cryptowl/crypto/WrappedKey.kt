package com.typedefai.cryptowl.crypto

/**
 * A wrapped key copy: AES-256-GCM ciphertext of a key plus its nonce and
 * auth tag. Stored as a row in `t_wrapped_key` / `t_data_encrypt_key`
 * or as an entry in `vault.meta`. AAD is the wrapped-key id (caller binds it).
 */
data class WrappedKey(
    val cipherText: ByteArray,
    val nonce: ByteArray,
    val authTag: ByteArray,
    val algorithm: String = AES_256_GCM,
) {
    companion object {
        const val AES_256_GCM = "AES-256-GCM"
    }
}
