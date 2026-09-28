package com.typedefai.cryptowl.vault

import com.typedefai.cryptowl.crypto.AesGcm
import com.typedefai.cryptowl.crypto.AuthEncryptedData
import com.typedefai.cryptowl.crypto.KdfService
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.crypto.RandomUtil
import com.typedefai.cryptowl.crypto.WrappedKey
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.json.JSONObject

/**
 * Secret-tier password CRUD (docs/design.md L2):
 *
 *   content = AES-256-GCM(DEK, JSON payload, AAD = encrypted_data id)
 *   DEK     = random per item, AES-256-GCM-wrapped by the KEK (AAD = DEK id)
 *   KEK     = unwrapped per access via BioKey (see [KekService])
 *
 * The payload is one JSON object `{username, password, url, notes}`; only the
 * title is L0 plaintext (SQLCipher), so the list renders without a fingerprint.
 */
class PasswordService(
    db: SQLiteDatabase,
    private val kdf: KdfService = KdfService(),
) {

    private val repo = PasswordRepository(db)

    fun list(): List<PasswordSummary> = repo.list()

    /** Creates a new S-tier entry; returns its id. */
    fun create(kek: ProtectedValue, draft: PasswordDraft): String {
        val now = System.currentTimeMillis()
        val passwordId = RandomUtil.generateUUID()
        val encryptedDataId = RandomUtil.generateUUID()
        val dekId = RandomUtil.generateUUID()
        val dek = ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(KEY_SIZE))
        try {
            val wrapped = kdf.wrapKey(dek, kek, aad = dekId.toByteArray(Charsets.UTF_8))
            val payload = draft.toJsonBytes()
            val contentNonce = RandomUtil.generateSecureBytes(AesGcm.NONCE_SIZE)
            val content = try {
                dek.use { raw ->
                    AesGcm.encrypt(raw, contentNonce, encryptedDataId.toByteArray(Charsets.UTF_8), payload)
                }
            } finally {
                payload.fill(0)
            }
            repo.create(
                passwordId = passwordId,
                encryptedDataId = encryptedDataId,
                dekId = dekId,
                title = draft.title,
                wrappedDekCiphertext = wrapped.cipherText,
                wrappedDekNonce = wrapped.nonce,
                wrappedDekTag = wrapped.authTag,
                content = content.cipherText,
                contentNonce = contentNonce,
                contentTag = content.authTag,
                now = now,
            )
            return passwordId
        } finally {
            dek.clear()
        }
    }

    /** Re-encrypts the payload and title in place (same DEK, fresh content nonce). */
    fun update(kek: ProtectedValue, draft: PasswordDraft) {
        val id = requireNotNull(draft.id) { "update requires a password id" }
        val encrypted = encryptedDataOf(id)
        val dek = unwrapDek(kek, encrypted)
        try {
            val payload = draft.toJsonBytes()
            val contentNonce = RandomUtil.generateSecureBytes(AesGcm.NONCE_SIZE)
            val content = try {
                dek.use { raw ->
                    AesGcm.encrypt(raw, contentNonce, encrypted.id.toByteArray(Charsets.UTF_8), payload)
                }
            } finally {
                payload.fill(0)
            }
            repo.update(
                encryptedDataId = encrypted.id,
                passwordId = id,
                title = draft.title,
                content = content.cipherText,
                contentNonce = contentNonce,
                contentTag = content.authTag,
                now = System.currentTimeMillis(),
            )
        } finally {
            dek.clear()
        }
    }

    /** Decrypts one entry with the per-access KEK. */
    fun detail(kek: ProtectedValue, id: String): PasswordDetail? {
        val record = repo.record(id) ?: return null
        val encrypted = repo.encryptedData(record.encryptedDataId) ?: return null
        val dek = unwrapDek(kek, encrypted)
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
                parseDetail(record, plain)
            } finally {
                plain.fill(0)
            }
        } finally {
            dek.clear()
        }
    }

    fun softDelete(id: String) {
        repo.softDelete(id, System.currentTimeMillis())
    }

    // ---------------------------------------------------------------- helpers

    private fun encryptedDataOf(passwordId: String): EncryptedDataRow {
        val record = repo.record(passwordId) ?: error("no such password: $passwordId")
        return repo.encryptedData(record.encryptedDataId) ?: error("missing encrypted data for $passwordId")
    }

    private fun unwrapDek(kek: ProtectedValue, encrypted: EncryptedDataRow): ProtectedValue {
        val dekRow = repo.dataEncryptKey(encrypted.dekId) ?: error("missing DEK ${encrypted.dekId}")
        return kdf.unwrapKey(
            wrapped = WrappedKey(dekRow.ciphertext, dekRow.nonce, dekRow.authTag),
            wrappingKey = kek,
            aad = dekRow.id.toByteArray(Charsets.UTF_8),
        )
    }

    private fun PasswordDraft.toJsonBytes(): ByteArray = JSONObject().apply {
        put("username", username)
        put("password", password)
        put("url", url)
        put("notes", notes)
    }.toString().toByteArray(Charsets.UTF_8)

    private fun parseDetail(record: PasswordRecord, plain: ByteArray): PasswordDetail {
        val json = JSONObject(String(plain, Charsets.UTF_8))
        return PasswordDetail(
            id = record.id,
            title = record.title,
            username = json.optString("username"),
            password = json.optString("password"),
            url = json.optString("url"),
            notes = json.optString("notes"),
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
        )
    }

    private companion object {
        const val KEY_SIZE = 32
    }
}
