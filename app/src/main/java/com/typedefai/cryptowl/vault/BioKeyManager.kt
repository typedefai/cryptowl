package com.typedefai.cryptowl.vault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * BioKey: an Android Keystore AES-GCM key bound to biometric authentication
 * (`setUserAuthenticationRequired(true, 0)`), StrongBox when available.
 * Every use requires a fresh BiometricPrompt — see docs/design.md
 * "Fingerprint / Biometric Unlock".
 */
object BioKeyManager {

    const val KEY_ALIAS = "cryptowl_biokey"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val GCM_TRANSFORMATION = "AES/GCM/NoPadding"

    fun hasBioKey(): Boolean =
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.containsAlias(KEY_ALIAS)

    /**
     * Creates the BioKey if missing. Best-effort StrongBox: falls back to
     * TEE-backed storage when the device has no StrongBox.
     */
    fun ensureBioKey() {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) return
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .apply {
                try {
                    setIsStrongBoxBacked(true)
                } catch (_: IllegalArgumentException) {
                    // StrongBox not available; TEE-backed is fine.
                }
            }
            .build()
        generator.init(spec)
        generator.generateKey()
    }

    /** Returns an ENCRYPT-mode cipher that must be authorized via BiometricPrompt. */
    fun createEncryptCipher(): Cipher {
        val key = loadKey()
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher
    }

    /**
     * Returns a DECRYPT-mode cipher initialized with the wrapped key's nonce
     * (GCM IV). Must be authorized via BiometricPrompt before [Cipher.doFinal].
     */
    fun createDecryptCipher(nonce: ByteArray): Cipher {
        val key = loadKey()
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE * 8, nonce))
        return cipher
    }

    private fun loadKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    private const val TAG_SIZE = 16
}
