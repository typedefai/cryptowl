package com.typedefai.cryptowl.vault

import android.content.Context
import com.typedefai.cryptowl.crypto.AesGcm
import com.typedefai.cryptowl.crypto.AuthEncryptedData
import com.typedefai.cryptowl.crypto.KdfParams
import com.typedefai.cryptowl.crypto.KdfService
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.crypto.RandomUtil
import com.typedefai.cryptowl.crypto.WrappedKey
import java.security.MessageDigest
import javax.crypto.Cipher
import net.zetetic.database.sqlcipher.SQLiteDatabase

/** Decrypted Top-Secret note (only after the two-factor gate). */
data class TopSecretNote(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Top-Secret tier (docs/design.md L3), true two-factor per access:
 *
 *   1. fingerprint → BioKey decrypts `ts_kek:biokey`  → TS-KEK_bio
 *   2. user types the secondary password → Argon2id   → TS-KEK_pw
 *   3. both must match exactly; then `top_secret_kek:ts_kek` is unwrapped
 *      with TS-KEK → TopSecretKEK → per-item DEK → content.
 *
 * `TS-KEK = Argon2id(secondaryPassword, secondarySalt)`; it is stored twice:
 * wrapped by BioKey (for the fingerprint factor and future re-wraps) and used
 * as the wrapping key for the random TopSecretKEK. Neither the secondary
 * password nor the KEKs are ever persisted in the clear.
 */
class TopSecretService(
    private val context: Context,
    private val db: SQLiteDatabase,
    private val kdf: KdfService = KdfService(),
    private val vaultId: String = VaultStore.DEFAULT_VAULT_ID,
) {

    // ------------------------------------------------------------------ setup

    fun isConfigured(): Boolean = rowExists(TS_KEK_ID) && rowExists(TOP_SECRET_KEK_ID)

    /** ENCRYPT cipher (AAD `ts_kek:biokey`) to authorize with a fingerprint. */
    fun prepareSetupCipher(): Cipher =
        BioKeyManager.createEncryptCipher().apply { updateAAD(TS_KEK_AAD) }

    /**
     * Persists the TS-KEK (wrapped by the biometric cipher) and a fresh
     * TopSecretKEK (wrapped by the TS-KEK). Called with the authorized cipher.
     */
    fun completeSetup(encryptCipher: Cipher, secondaryPassword: ProtectedValue) {
        val meta = loadMeta()
        val salt = meta.salts.secondary ?: error("vault.meta has no secondary salt")
        val tsKek = kdf.createSecondaryKey(secondaryPassword, salt, paramsOf(meta))
        try {
            val wrappedTsKek = tsKek.use { encryptCipher.doFinal(it) }
            insertWrappedKey(TS_KEK_ID, TS_KEK_ROLE, TS_KEK_WRAPPER, encryptCipher.iv, wrappedTsKek)

            val topSecretKek = ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(KEY_SIZE))
            try {
                val wrapped = kdf.wrapKey(topSecretKek, tsKek, aad = TOP_SECRET_KEK_AAD)
                insertWrappedKey(
                    TOP_SECRET_KEK_ID,
                    TOP_SECRET_KEK_ROLE,
                    TOP_SECRET_KEK_WRAPPER,
                    wrapped.nonce,
                    wrapped.cipherText + wrapped.authTag,
                )
            } finally {
                topSecretKek.clear()
            }
        } finally {
            tsKek.clear()
        }
    }

    // ------------------------------------------------------------- per access

    /** DECRYPT cipher for `ts_kek:biokey` (factor 1). */
    fun prepareUnlockCipher(): Cipher {
        val row = loadRow(TS_KEK_ID) ?: error("no $TS_KEK_ID row in t_wrapped_key")
        return BioKeyManager.createDecryptCipher(row.nonce).apply { updateAAD(TS_KEK_AAD) }
    }

    /** Fingerprint factor: unwraps the biometric TS-KEK copy. */
    fun unlockTsKek(cipher: Cipher): ProtectedValue {
        val row = loadRow(TS_KEK_ID) ?: error("no $TS_KEK_ID row in t_wrapped_key")
        val plain = try {
            cipher.doFinal(row.ciphertext + row.authTag)
        } catch (e: Exception) {
            throw VaultOpenException("fingerprint key cannot unwrap the Top-Secret KEK")
        }
        return try {
            ProtectedValue.fromBinary(plain)
        } finally {
            plain.fill(0)
        }
    }

    /**
     * Password factor + unwrap: re-derives the TS-KEK from the typed secondary
     * password, requires it to equal the biometric copy, then unwraps the
     * TopSecretKEK. Both factors are enforced before any content is touched.
     */
    fun unlockTopSecretKek(tsKekBio: ProtectedValue, secondaryPassword: ProtectedValue): ProtectedValue {
        val meta = loadMeta()
        val salt = meta.salts.secondary ?: error("vault.meta has no secondary salt")
        val tsKekPw = kdf.createSecondaryKey(secondaryPassword, salt, paramsOf(meta))
        try {
            val bio = tsKekBio.binaryValue()
            val pw = tsKekPw.binaryValue()
            try {
                if (!MessageDigest.isEqual(bio, pw)) {
                    throw VaultOpenException("secondary password is incorrect")
                }
            } finally {
                bio.fill(0)
                pw.fill(0)
            }
        } finally {
            tsKekPw.clear()
        }
        val row = loadRow(TOP_SECRET_KEK_ID) ?: error("no $TOP_SECRET_KEK_ID row in t_wrapped_key")
        return kdf.unwrapKey(
            wrapped = WrappedKey(row.ciphertext, row.nonce, row.authTag),
            wrappingKey = tsKekBio,
            aad = TOP_SECRET_KEK_AAD,
        )
    }

    // ------------------------------------------------------------------ notes

    fun list(): List<NoteSummary> = NoteRepository(db).listBy(CLASSIFICATION)

    fun create(title: String, content: String, topSecretKek: ProtectedValue): String {
        val noteId = RandomUtil.generateUUID()
        val encryptedDataId = RandomUtil.generateUUID()
        val dekId = RandomUtil.generateUUID()
        val dek = ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(KEY_SIZE))
        try {
            val wrapped = kdf.wrapKey(dek, topSecretKek, aad = dekId.toByteArray(Charsets.UTF_8))
            val payload = content.toByteArray(Charsets.UTF_8)
            val nonce = RandomUtil.generateSecureBytes(AesGcm.NONCE_SIZE)
            val encrypted = try {
                dek.use { raw -> AesGcm.encrypt(raw, nonce, encryptedDataId.toByteArray(Charsets.UTF_8), payload) }
            } finally {
                payload.fill(0)
            }
            db.beginTransaction()
            try {
                insertEncryptedData(
                    encryptedDataId, dekId, wrapped.cipherText, wrapped.nonce, wrapped.authTag,
                    encrypted.cipherText, nonce, encrypted.authTag,
                )
                val noteId = NoteRepository(db).createEncrypted(title, CLASSIFICATION, encryptedDataId)
                db.setTransactionSuccessful()
                return noteId
            } finally {
                db.endTransaction()
            }
        } finally {
            dek.clear()
        }
    }

    fun read(id: String, topSecretKek: ProtectedValue): TopSecretNote? {
        val ref = NoteRepository(db).encryptedRef(id) ?: return null
        val encrypted = encryptedRow(ref.encryptedDataId) ?: return null
        val dekRow = dekRow(encrypted.dekId) ?: return null
        val dek = kdf.unwrapKey(
            wrapped = dekRow,
            wrappingKey = topSecretKek,
            aad = encrypted.dekId.toByteArray(Charsets.UTF_8),
        )
        return try {
            val plain = dek.use { raw ->
                AesGcm.decrypt(
                    raw,
                    encrypted.nonce,
                    encrypted.id.toByteArray(Charsets.UTF_8),
                    AuthEncryptedData(encrypted.content, encrypted.authTag),
                )
            }
            try {
                TopSecretNote(
                    id = id,
                    title = ref.title,
                    content = String(plain, Charsets.UTF_8),
                    createdAt = ref.createdAt,
                    updatedAt = ref.updatedAt,
                )
            } finally {
                plain.fill(0)
            }
        } finally {
            dek.clear()
        }
    }

    fun update(id: String, title: String, content: String, topSecretKek: ProtectedValue) {
        val ref = NoteRepository(db).encryptedRef(id) ?: error("no Top-Secret note: $id")
        val encrypted = encryptedRow(ref.encryptedDataId) ?: error("missing encrypted data for $id")
        val dekRow = dekRow(encrypted.dekId) ?: error("missing DEK ${encrypted.dekId}")
        val dek = kdf.unwrapKey(
            wrapped = dekRow,
            wrappingKey = topSecretKek,
            aad = encrypted.dekId.toByteArray(Charsets.UTF_8),
        )
        try {
            val payload = content.toByteArray(Charsets.UTF_8)
            val nonce = RandomUtil.generateSecureBytes(AesGcm.NONCE_SIZE)
            val updated = try {
                dek.use { raw -> AesGcm.encrypt(raw, nonce, encrypted.id.toByteArray(Charsets.UTF_8), payload) }
            } finally {
                payload.fill(0)
            }
            val now = System.currentTimeMillis()
            db.beginTransaction()
            try {
                db.execSQL(
                    "UPDATE t_encrypted_data SET content = ?, nonce = ?, auth_tag = ?, updated_at = ? WHERE id = ?",
                    arrayOf<Any>(updated.cipherText, nonce, updated.authTag, now, encrypted.id),
                )
                NoteRepository(db).touchTitle(id, title)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            dek.clear()
        }
    }

    fun delete(id: String) {
        NoteRepository(db).softDelete(id)
    }

    // ---------------------------------------------------------------- helpers

    private data class Row(val ciphertext: ByteArray, val nonce: ByteArray, val authTag: ByteArray)

    private data class EncryptedRow(
        val id: String,
        val dekId: String,
        val content: ByteArray,
        val nonce: ByteArray,
        val authTag: ByteArray,
    )

    private fun loadMeta(): VaultMeta {
        val metaFile = VaultStore.metaFile(context, vaultId)
        if (!metaFile.exists()) throw VaultOpenException("not a vault: $vaultId")
        return try {
            VaultMetaJson.decode(metaFile.readText())
        } catch (e: Exception) {
            throw VaultOpenException("corrupt vault.meta: ${e.message}")
        }
    }

    private fun paramsOf(meta: VaultMeta): KdfParams = KdfParams(
        algorithm = meta.kdf.algorithm,
        mCostKiB = meta.kdf.mKib,
        tCost = meta.kdf.t,
        parallelism = meta.kdf.p,
    )

    private fun rowExists(id: String): Boolean =
        db.rawQuery("SELECT 1 FROM t_wrapped_key WHERE id = ? AND deleted_at IS NULL LIMIT 1", arrayOf(id))
            .use { it.moveToFirst() }

    private fun loadRow(id: String): Row? =
        db.rawQuery(
            "SELECT ciphertext, nonce, auth_tag FROM t_wrapped_key WHERE id = ? AND deleted_at IS NULL LIMIT 1",
            arrayOf(id),
        ).use { c -> if (c.moveToFirst()) Row(c.getBlob(0), c.getBlob(1), c.getBlob(2)) else null }

    private fun insertWrappedKey(id: String, role: String, wrapper: String, nonce: ByteArray, ciphertextWithTag: ByteArray) {
        val now = System.currentTimeMillis()
        val tagSize = AesGcm.TAG_SIZE_BYTES
        db.execSQL(
            """INSERT OR REPLACE INTO t_wrapped_key
                   (id, role, wrapper, algorithm, ciphertext, nonce, auth_tag, created_at, updated_at)
               VALUES (?, ?, ?, 'AES-256-GCM', ?, ?, ?, ?, ?)""",
            arrayOf<Any>(
                id,
                role,
                wrapper,
                ciphertextWithTag.copyOf(ciphertextWithTag.size - tagSize),
                nonce,
                ciphertextWithTag.copyOfRange(ciphertextWithTag.size - tagSize, ciphertextWithTag.size),
                now,
                now,
            ),
        )
    }

    private fun insertEncryptedData(
        encryptedDataId: String,
        dekId: String,
        dekCiphertext: ByteArray,
        dekNonce: ByteArray,
        dekTag: ByteArray,
        content: ByteArray,
        contentNonce: ByteArray,
        contentTag: ByteArray,
    ) {
        val now = System.currentTimeMillis()
        db.execSQL(
            """INSERT INTO t_data_encrypt_key (id, algorithm, ciphertext, nonce, auth_tag, created_at, updated_at)
               VALUES (?, 'AES-256-GCM', ?, ?, ?, ?, ?)""",
            arrayOf<Any>(dekId, dekCiphertext, dekNonce, dekTag, now, now),
        )
        db.execSQL(
            """INSERT INTO t_encrypted_data (id, dek_id, algorithm, content, nonce, auth_tag, created_at, updated_at)
               VALUES (?, ?, 'AES-256-GCM', ?, ?, ?, ?, ?)""",
            arrayOf<Any>(encryptedDataId, dekId, content, contentNonce, contentTag, now, now),
        )
    }

    private fun encryptedRow(id: String): EncryptedRow? =
        db.rawQuery(
            "SELECT id, dek_id, content, nonce, auth_tag FROM t_encrypted_data WHERE id = ? AND deleted_at IS NULL",
            arrayOf(id),
        ).use { c ->
            if (c.moveToFirst()) EncryptedRow(
                id = c.getString(0),
                dekId = c.getString(1),
                content = c.getBlob(2),
                nonce = c.getBlob(3),
                authTag = c.getBlob(4),
            ) else null
        }

    private fun dekRow(id: String): WrappedKey? =
        db.rawQuery("SELECT ciphertext, nonce, auth_tag FROM t_data_encrypt_key WHERE id = ?", arrayOf(id))
            .use { c ->
                if (c.moveToFirst()) WrappedKey(c.getBlob(0), c.getBlob(1), c.getBlob(2)) else null
            }

    private companion object {
        const val KEY_SIZE = 32
        const val CLASSIFICATION = "T"
        const val TS_KEK_ID = "ts_kek:biokey"
        const val TS_KEK_ROLE = "ts_kek"
        const val TS_KEK_WRAPPER = "biokey"
        const val TOP_SECRET_KEK_ID = "top_secret_kek:ts_kek"
        const val TOP_SECRET_KEK_ROLE = "top_secret_kek"
        const val TOP_SECRET_KEK_WRAPPER = "ts_kek"
        val TS_KEK_AAD = TS_KEK_ID.toByteArray(Charsets.UTF_8)
        val TOP_SECRET_KEK_AAD = TOP_SECRET_KEK_ID.toByteArray(Charsets.UTF_8)
    }
}
