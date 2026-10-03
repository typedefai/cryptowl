package com.typedefai.cryptowl.vault

import com.typedefai.cryptowl.crypto.CryptoLog
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.crypto.RandomUtil
import javax.crypto.Cipher
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * The Secret-tier KEK envelope: a random 32-byte key wrapped by the Keystore
 * BioKey and stored as `kek:biokey` in `t_wrapped_key`. The password chain
 * never wraps it — every access requires a fresh BiometricPrompt (L2).
 *
 * Unlike the older `vault_key:biokey` copy (which predates the rule), the KEK
 * is wrapped with AAD = wrapped-key id (`kek:biokey`), per docs/design.md.
 */
class KekService(private val db: SQLiteDatabase) {

    /** True when this vault has a KEK copy. */
    fun exists(): Boolean =
        db.rawQuery("SELECT 1 FROM t_wrapped_key WHERE id = '$KEK_ID' AND deleted_at IS NULL LIMIT 1", null)
            .use { it.moveToFirst() }

    /** ENCRYPT cipher bound to the BioKey (AAD applied); authorize via prompt. */
    fun prepareCreateCipher(): Cipher =
        BioKeyManager.createEncryptCipher().apply { updateAAD(AAD) }

    /** DECRYPT cipher bound to the stored nonce (AAD applied). */
    fun prepareUnlockCipher(): Cipher {
        val wrapped = loadWrapped() ?: error("no $KEK_ID row in t_wrapped_key")
        CryptoLog.d(C, "prepareUnlockCipher: $KEK_ID nonce=${wrapped.nonce.toHexString(8)} aad='$KEK_ID'")
        return BioKeyManager.createDecryptCipher(wrapped.nonce).apply { updateAAD(AAD) }
    }

    /**
     * Generates a fresh KEK, wraps it with the biometric-authorized [cipher]
     * and stores the copy; the plaintext KEK is returned for the operation.
     */
    fun completeCreate(cipher: Cipher): ProtectedValue {
        val kekBytes = RandomUtil.generateSecureBytes(KEY_SIZE)
        val kek = ProtectedValue.fromBinary(kekBytes)
        try {
            val encrypted = cipher.doFinal(kekBytes)
            val now = System.currentTimeMillis()
            db.execSQL(
                """INSERT OR REPLACE INTO t_wrapped_key
                       (id, role, wrapper, algorithm, ciphertext, nonce, auth_tag, created_at, updated_at)
                   VALUES (?, 'kek', 'biokey', 'AES-256-GCM', ?, ?, ?, ?, ?)""",
                arrayOf<Any>(
                    KEK_ID,
                    encrypted.copyOf(encrypted.size - TAG_SIZE),
                    cipher.iv,
                    encrypted.copyOfRange(encrypted.size - TAG_SIZE, encrypted.size),
                    now,
                    now,
                ),
            )
            CryptoLog.d(C, "completeCreate: $KEK_ID wrapped kek(${CryptoLog.key(kek)}) nonce=${cipher.iv.toHexString(8)} " +
                "cipher=${encrypted.size - TAG_SIZE}B tag=${TAG_SIZE}B")
            return kek
        } catch (e: Throwable) {
            kek.clear()
            throw e
        } finally {
            kekBytes.fill(0)
        }
    }

    /** Unwraps the stored KEK with the biometric-authorized [cipher]. */
    fun unlock(cipher: Cipher): ProtectedValue {
        val wrapped = loadWrapped() ?: error("no $KEK_ID row in t_wrapped_key")
        val plain = try {
            cipher.doFinal(wrapped.ciphertext + wrapped.authTag)
        } catch (e: Throwable) {
            CryptoLog.e(C, "unlock: $KEK_ID unwrap FAILED (fingerprint/AAD mismatch)", e)
            throw IllegalStateException("fingerprint key cannot unwrap the KEK")
        }
        return try {
            ProtectedValue.fromBinary(plain).also { CryptoLog.d(C, "unlock: KEK unwrapped kek(${CryptoLog.key(it)})") }
        } finally {
            plain.fill(0)
        }
    }

    private data class Wrapped(val ciphertext: ByteArray, val nonce: ByteArray, val authTag: ByteArray)

    private fun loadWrapped(): Wrapped? =
        db.rawQuery(
            "SELECT ciphertext, nonce, auth_tag FROM t_wrapped_key WHERE id = '$KEK_ID' AND deleted_at IS NULL LIMIT 1",
            null,
        ).use { c ->
            if (c.moveToFirst()) Wrapped(c.getBlob(0), c.getBlob(1), c.getBlob(2)) else null
        }

    private companion object {
        const val C = "KekService"
        const val KEK_ID = "kek:biokey"
        val AAD = KEK_ID.toByteArray(Charsets.UTF_8)
        const val KEY_SIZE = 32
        const val TAG_SIZE = 16
    }
}
