package com.typedefai.cryptowl.vault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.typedefai.cryptowl.crypto.CryptoLog
import androidx.core.content.edit
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.crypto.RandomUtil
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The 32-byte Device Secret: generated at first vault creation, stored in
 * Android Keystore (non-exportable AES-GCM key) with only the wrapped copy
 * on app storage. See docs/design.md — Device Secret.
 */
object DeviceSecretStore {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "cryptowl_device_secret"
    private const val PREFS = "cryptowl.vault"
    private const val PREFS_KEY = "device_secret_wrapped.v1"
    private const val GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val DEVICE_SECRET_SIZE = 32
    private const val TAG_BITS = 128
    private const val C = "DeviceSecretStore"

    /**
     * Returns the Device Secret, creating it on first use.
     * Throws on Keystore failure (e.g. key invalidated after factory reset).
     */
    @Synchronized
    fun getOrCreate(context: Context): ProtectedValue {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(PREFS_KEY, null)
        if (stored != null) {
            val secret = decrypt(loadKey(), stored)
            CryptoLog.d(C, "getOrCreate: loaded from prefs=$PREFS ($PREFS_KEY) secret(${CryptoLog.key(secret)})")
            return secret
        }
        CryptoLog.d(C, "getOrCreate: creating new device secret (prefs=$PREFS key=$PREFS_KEY)")
        val secret = RandomUtil.generateSecureBytes(DEVICE_SECRET_SIZE)
        val wrapped = encrypt(loadOrCreateKey(), secret)
        val encoded = Base64.encodeToString(wrapped, Base64.NO_WRAP)
        prefs.edit { putString(PREFS_KEY, encoded) }
        return ProtectedValue.fromBinary(secret).also {
            CryptoLog.d(C, "getOrCreate: created device secret secret(${CryptoLog.key(it)}) wrapped=${wrapped.size}B")
            secret.fill(0)
        }
    }

    /** True if a Device Secret exists (i.e. a vault was created on this device before). */
    fun exists(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(PREFS_KEY)

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        CryptoLog.d(C, "loadOrCreateKey: creating Keystore key alias=$KEY_ALIAS")
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun loadKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    private fun encrypt(key: SecretKey, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintext)
        CryptoLog.d(C, "encrypt: wrapped device secret iv=${cipher.iv.joinToString("") { "%02x".format(it) }} blob=${cipher.iv.size + ciphertext.size}B")
        // iv(12) || ciphertext || authTag(16)
        return cipher.iv + ciphertext
    }

    private fun decrypt(key: SecretKey, wrapped: String): ProtectedValue {
        val blob = Base64.decode(wrapped, Base64.NO_WRAP)
        val iv = blob.copyOfRange(0, 12)
        val ciphertext = blob.copyOfRange(12, blob.size)
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val secret = cipher.doFinal(ciphertext)
        return ProtectedValue.fromBinary(secret).also { secret.fill(0) }
    }
}
