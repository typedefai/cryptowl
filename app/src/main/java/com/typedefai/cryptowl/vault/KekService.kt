package com.typedefai.cryptowl.vault

import com.typedefai.cryptowl.crypto.CryptoLog
import com.typedefai.cryptowl.crypto.KdfService
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.crypto.RandomUtil
import com.typedefai.cryptowl.crypto.WrappedKey
import javax.crypto.Cipher

/**
 * The Secret-tier KEK envelope: a random 32-byte key wrapped by the Keystore
 * BioKey and stored as `kek:biokey` in `t_wrapped_key`. The password chain
 * never wraps it on Android — every access requires a fresh BiometricPrompt
 * (docs/design.md L2), and the KEK is never cached between accesses.
 *
 * Imported desktop vaults may carry a `kek:smk` dev-binding row (KEK wrapped
 * with the SMK). [importDesktopKek] performs a one-time conversion: unwrap
 * with the master-password SMK (re-entered for that access), rewrap under the
 * BioKey as `kek:biokey`, and delete the stale `kek:smk` row.
 */
class KekService(
    private val db: SqlCipherDb,
    private val kdf: KdfService = KdfService(),
) {

    /** True when this vault has a BioKey-wrapped KEK. */
    fun exists(): Boolean = rowExists(KEK_ID)

    /** True when the vault still carries the desktop `kek:smk` binding. */
    fun hasDesktopKek(): Boolean = rowExists(DESKTOP_KEK_ID)

    /** ENCRYPT cipher bound to the BioKey (AAD applied); authorize via prompt. */
    fun prepareCreateCipher(): Cipher =
        BioKeyManager.createEncryptCipher().apply { updateAAD(AAD) }

    /** DECRYPT cipher bound to the stored nonce (AAD applied). */
    fun prepareUnlockCipher(): Cipher {
        val wrapped = loadWrapped(KEK_ID) ?: error("no $KEK_ID row in t_wrapped_key")
        CryptoLog.d(C, "prepareUnlockCipher: $KEK_ID nonce=${wrapped.nonce.toHexString(8)}")
        return BioKeyManager.createDecryptCipher(wrapped.nonce).apply { updateAAD(AAD) }
    }

    /** Generates a fresh KEK, wraps it with the biometric cipher, stores it. */
    fun completeCreate(cipher: Cipher): ProtectedValue {
        val kekBytes = RandomUtil.generateSecureBytes(KEY_SIZE)
        val kek = ProtectedValue.fromBinary(kekBytes)
        try {
            val encrypted = cipher.doFinal(kekBytes)
            val now = System.currentTimeMillis()
            db.execSQL(
                "INSERT OR REPLACE INTO t_wrapped_key " +
                    "(id, role, wrapper, algorithm, ciphertext, nonce, auth_tag, created_at, updated_at) " +
                    "VALUES (?, 'kek', 'biokey', 'AES-256-GCM', ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    KEK_ID,
                    encrypted.copyOf(encrypted.size - TAG_SIZE),
                    cipher.iv,
                    encrypted.copyOfRange(encrypted.size - TAG_SIZE, encrypted.size),
                    now,
                    now,
                ),
            )
            CryptoLog.d(C, "completeCreate: $KEK_ID stored kek(${CryptoLog.key(kek)})")
            return kek
        } catch (t: Throwable) {
            kek.clear()
            throw t
        } finally {
            kekBytes.fill(0)
        }
    }

    /** Unwraps the stored KEK with the biometric-authorized cipher. */
    fun unlock(cipher: Cipher): ProtectedValue {
        val wrapped = loadWrapped(KEK_ID) ?: error("no $KEK_ID row in t_wrapped_key")
        val plain = try {
            cipher.doFinal(wrapped.ciphertext + wrapped.authTag)
        } catch (e: Throwable) {
            CryptoLog.e(C, "unlock: $KEK_ID unwrap failed", e)
            throw IllegalStateException("fingerprint key cannot unwrap the KEK")
        }
        return try {
            ProtectedValue.fromBinary(plain)
        } finally {
            plain.fill(0)
        }
    }

    /**
     * One-time import of a desktop `kek:smk` row: unwrap with the current SMK
     * (master password re-entered for this access), rewrap with the authorized
     * BioKey cipher, store `kek:biokey` and delete `kek:smk`.
     *
     * Returns null when there is nothing to import.
     */
    fun importDesktopKek(smk: ProtectedValue, cipher: Cipher): ProtectedValue? {
        val wrapped = loadWrapped(DESKTOP_KEK_ID) ?: return null
        val wrappingKey = kdf.vaultKey(smk)
        val kek = try {
            kdf.unwrapKey(
                wrapped = WrappedKey(wrapped.ciphertext, wrapped.nonce, wrapped.authTag),
                wrappingKey = wrappingKey,
                aad = DESKTOP_KEK_ID.toByteArray(Charsets.UTF_8),
            )
        } finally {
            wrappingKey.clear()
        }
        try {
            val kekBytes = kek.use { it.copyOf() }
            val encrypted = try {
                cipher.doFinal(kekBytes)
            } finally {
                kekBytes.fill(0)
            }
            val now = System.currentTimeMillis()
            db.transaction {
                db.execSQL(
                    "INSERT OR REPLACE INTO t_wrapped_key " +
                        "(id, role, wrapper, algorithm, ciphertext, nonce, auth_tag, created_at, updated_at) " +
                        "VALUES (?, 'kek', 'biokey', 'AES-256-GCM', ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(
                        KEK_ID,
                        encrypted.copyOf(encrypted.size - TAG_SIZE),
                        cipher.iv,
                        encrypted.copyOfRange(encrypted.size - TAG_SIZE, encrypted.size),
                        now,
                        now,
                    ),
                )
                db.execSQL("DELETE FROM t_wrapped_key WHERE id = ?", arrayOf<Any?>(DESKTOP_KEK_ID))
            }
            CryptoLog.d(C, "importDesktopKek: $DESKTOP_KEK_ID -> $KEK_ID (desktop binding consumed)")
            return kek
        } catch (t: Throwable) {
            kek.clear()
            throw t
        }
    }

    private data class Wrapped(val ciphertext: ByteArray, val nonce: ByteArray, val authTag: ByteArray)

    private fun rowExists(id: String): Boolean =
        db.rawQuery("SELECT 1 FROM t_wrapped_key WHERE id = ? AND deleted_at IS NULL LIMIT 1", arrayOf<Any?>(id))
            .use { it.moveToFirst() }

    private fun loadWrapped(id: String): Wrapped? =
        db.rawQuery(
            "SELECT ciphertext, nonce, auth_tag FROM t_wrapped_key WHERE id = ? AND deleted_at IS NULL LIMIT 1",
            arrayOf<Any?>(id),
        ).use { c ->
            if (c.moveToFirst()) Wrapped(c.getBlob(0), c.getBlob(1), c.getBlob(2)) else null
        }

    private companion object {
        const val C = "KekService"
        const val KEK_ID = "kek:biokey"
        const val DESKTOP_KEK_ID = "kek:smk"
        val AAD = KEK_ID.toByteArray(Charsets.UTF_8)
        const val KEY_SIZE = 32
        const val TAG_SIZE = 16
    }
}
