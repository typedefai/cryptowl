package com.typedefai.cryptowl.vault

import com.typedefai.cryptowl.crypto.CryptoLog
import net.zetetic.database.sqlcipher.SQLiteDatabase

/** L0 list row: only the plaintext title and timestamps. */
data class PasswordSummary(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Metadata of one password entry (S tier; content lives in t_encrypted_data). */
data class PasswordRecord(
    val id: String,
    val title: String,
    val encryptedDataId: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Plaintext draft from the editor. [id] is null for a new entry. */
data class PasswordDraft(
    val id: String? = null,
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
)

/** Decrypted detail (only after a fingerprint-authorized KEK unwrap). */
data class PasswordDetail(
    val id: String,
    val title: String,
    val username: String,
    val password: String,
    val url: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** `t_encrypted_data` row: the AES-256-GCM ciphertext of the entry payload. */
data class EncryptedDataRow(
    val id: String,
    val dekId: String,
    val content: ByteArray,
    val nonce: ByteArray,
    val authTag: ByteArray,
)

/** `t_data_encrypt_key` row: the entry's per-item DEK, wrapped by the KEK. */
data class DataEncryptKeyRow(
    val id: String,
    val ciphertext: ByteArray,
    val nonce: ByteArray,
    val authTag: ByteArray,
)

/**
 * Raw SQLCipher access to the password tables (no Room — see AGENTS.md).
 * Metadata and payload are separate rows so the list never touches ciphertext.
 */
class PasswordRepository(private val db: SQLiteDatabase) {

    private companion object {
        const val C = "PasswordRepository"
    }


    fun list(): List<PasswordSummary> =
        db.rawQuery(
            """SELECT id, title, created_at, updated_at FROM t_password
               WHERE deleted_at IS NULL
               ORDER BY updated_at DESC, id DESC""",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(PasswordSummary(
                        id = c.getString(0),
                        title = c.getString(1),
                        createdAt = c.getLong(2),
                        updatedAt = c.getLong(3),
                    ))
                }
            }
        }

    fun record(id: String): PasswordRecord? =
        db.rawQuery(
            """SELECT id, title, encrypted_data_id, created_at, updated_at FROM t_password
               WHERE id = ? AND deleted_at IS NULL""",
            arrayOf(id),
        ).use { c ->
            if (c.moveToFirst()) PasswordRecord(
                id = c.getString(0),
                title = c.getString(1),
                encryptedDataId = c.getString(2),
                createdAt = c.getLong(3),
                updatedAt = c.getLong(4),
            ) else null
        }

    fun encryptedData(id: String): EncryptedDataRow? =
        db.rawQuery(
            """SELECT id, dek_id, content, nonce, auth_tag FROM t_encrypted_data
               WHERE id = ? AND deleted_at IS NULL""",
            arrayOf(id),
        ).use { c ->
            if (c.moveToFirst()) EncryptedDataRow(
                id = c.getString(0),
                dekId = c.getString(1),
                content = c.getBlob(2),
                nonce = c.getBlob(3),
                authTag = c.getBlob(4),
            ) else null
        }

    fun dataEncryptKey(id: String): DataEncryptKeyRow? =
        db.rawQuery(
            "SELECT id, ciphertext, nonce, auth_tag FROM t_data_encrypt_key WHERE id = ?",
            arrayOf(id),
        ).use { c ->
            if (c.moveToFirst()) DataEncryptKeyRow(
                id = c.getString(0),
                ciphertext = c.getBlob(1),
                nonce = c.getBlob(2),
                authTag = c.getBlob(3),
            ) else null
        }

    /** Inserts the DEK, its encrypted payload and the entry row atomically. */
    fun create(
        passwordId: String,
        encryptedDataId: String,
        dekId: String,
        title: String,
        wrappedDekCiphertext: ByteArray,
        wrappedDekNonce: ByteArray,
        wrappedDekTag: ByteArray,
        content: ByteArray,
        contentNonce: ByteArray,
        contentTag: ByteArray,
        now: Long,
    ) {
        CryptoLog.d(C, "create: passwordId=$passwordId encryptedDataId=$encryptedDataId dekId=$dekId " +
            "content=${content.size}B dekAad=$dekId contentAad=$encryptedDataId")
        db.beginTransaction()
        try {
            db.execSQL(
                """INSERT INTO t_data_encrypt_key
                       (id, algorithm, ciphertext, nonce, auth_tag, created_at, updated_at)
                   VALUES (?, 'AES-256-GCM', ?, ?, ?, ?, ?)""",
                arrayOf<Any>(dekId, wrappedDekCiphertext, wrappedDekNonce, wrappedDekTag, now, now),
            )
            db.execSQL(
                """INSERT INTO t_encrypted_data
                       (id, dek_id, algorithm, content, nonce, auth_tag, created_at, updated_at)
                   VALUES (?, ?, 'AES-256-GCM', ?, ?, ?, ?, ?)""",
                arrayOf<Any>(encryptedDataId, dekId, content, contentNonce, contentTag, now, now),
            )
            db.execSQL(
                """INSERT INTO t_password
                       (id, type, classification, title, encrypted_data_id, created_at, updated_at)
                   VALUES (?, 1, 'S', ?, ?, ?, ?)""",
                arrayOf<Any>(passwordId, title, encryptedDataId, now, now),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Rewrites the payload ciphertext and the plaintext title of an entry. */
    fun update(
        encryptedDataId: String,
        passwordId: String,
        title: String,
        content: ByteArray,
        contentNonce: ByteArray,
        contentTag: ByteArray,
        now: Long,
    ) {
        CryptoLog.d(C, "update: passwordId=$passwordId encryptedDataId=$encryptedDataId " +
            "content=${content.size}B contentAad=$encryptedDataId")
        db.beginTransaction()
        try {
            db.execSQL(
                """UPDATE t_encrypted_data
                   SET content = ?, nonce = ?, auth_tag = ?, updated_at = ?
                   WHERE id = ?""",
                arrayOf<Any>(content, contentNonce, contentTag, now, encryptedDataId),
            )
            db.execSQL(
                "UPDATE t_password SET title = ?, updated_at = ? WHERE id = ?",
                arrayOf<Any>(title, now, passwordId),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun softDelete(id: String, now: Long) {
        CryptoLog.d(C, "softDelete: passwordId=$id")
        db.execSQL(
            "UPDATE t_password SET deleted_at = ?, updated_at = ? WHERE id = ?",
            arrayOf<Any>(now, now, id),
        )
    }
}
