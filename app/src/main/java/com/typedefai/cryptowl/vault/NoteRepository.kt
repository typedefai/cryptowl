package com.typedefai.cryptowl.vault

import com.typedefai.cryptowl.crypto.CryptoLog
import net.zetetic.database.sqlcipher.SQLiteDatabase

/** List row for the notes list (title/pin/timestamps only). */
data class NoteSummary(
    val id: String,
    val title: String,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Full note: C tier, content is L1 plaintext inside the SQLCipher DB. */
data class Note(
    val id: String,
    val title: String,
    val content: String,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Draft from the editor; [id] is null for a new note. */
data class NoteDraft(
    val id: String? = null,
    val title: String = "",
    val content: String = "",
    val pinned: Boolean = false,
)

/**
 * Raw SQLCipher access to `t_note` (migration v4). Confidential tier: no
 * fingerprint, no per-item DEK — SQLCipher is the boundary (docs/design.md L1).
 * `encrypted_data_id` is reserved for a future S/T escalation.
 */
class NoteRepository(private val db: SQLiteDatabase) {

    private companion object {
        const val C = "NoteRepository"
    }


    fun list(): List<NoteSummary> =
        db.rawQuery(
            """SELECT id, title, pinned, created_at, updated_at FROM t_note
               WHERE deleted_at IS NULL AND classification = 'C'
               ORDER BY pinned DESC, updated_at DESC, id DESC""",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(NoteSummary(
                        id = c.getString(0),
                        title = c.getString(1),
                        pinned = c.getInt(2) != 0,
                        createdAt = c.getLong(3),
                        updatedAt = c.getLong(4),
                    ))
                }
            }
        }

    fun get(id: String): Note? =
        db.rawQuery(
            "SELECT id, title, content, pinned, created_at, updated_at FROM t_note WHERE id = ? AND deleted_at IS NULL",
            arrayOf(id),
        ).use { c ->
            if (c.moveToFirst()) Note(
                id = c.getString(0),
                title = c.getString(1),
                content = c.getString(2) ?: "",
                pinned = c.getInt(3) != 0,
                createdAt = c.getLong(4),
                updatedAt = c.getLong(5),
            ) else null
        }

    fun create(draft: NoteDraft): String {
        val id = com.typedefai.cryptowl.crypto.RandomUtil.generateUUID()
        CryptoLog.d(C, "create: noteId=$id title='${draft.title}' content=${draft.content.length}ch pinned=${draft.pinned} (classification C, SQLCipher only)")
        val now = System.currentTimeMillis()
        db.execSQL(
            """INSERT INTO t_note (id, classification, title, content, pinned, created_at, updated_at)
               VALUES (?, 'C', ?, ?, ?, ?, ?)""",
            arrayOf<Any>(id, draft.title, draft.content, if (draft.pinned) 1 else 0, now, now),
        )
        return id
    }

    fun update(id: String, draft: NoteDraft) {
        CryptoLog.d(C, "update: noteId=$id title='${draft.title}' content=${draft.content.length}ch")
        db.execSQL(
            "UPDATE t_note SET title = ?, content = ?, pinned = ?, updated_at = ? WHERE id = ?",
            arrayOf<Any>(draft.title, draft.content, if (draft.pinned) 1 else 0, System.currentTimeMillis(), id),
        )
    }

    fun setPinned(id: String, pinned: Boolean) {
        db.execSQL(
            "UPDATE t_note SET pinned = ?, updated_at = ? WHERE id = ?",
            arrayOf<Any>(if (pinned) 1 else 0, System.currentTimeMillis(), id),
        )
    }

    fun softDelete(id: String) {
        CryptoLog.d(C, "softDelete: noteId=$id")
        val now = System.currentTimeMillis()
        db.execSQL("UPDATE t_note SET deleted_at = ?, updated_at = ? WHERE id = ?", arrayOf<Any>(now, now, id))
    }

    // ------------------------------------------------- encrypted (T tier)

    /** Titles of one classification (L0 metadata; content stays encrypted). */
    fun listBy(classification: String): List<NoteSummary> =
        db.rawQuery(
            """SELECT id, title, pinned, created_at, updated_at FROM t_note
               WHERE deleted_at IS NULL AND classification = ?
               ORDER BY updated_at DESC, id DESC""",
            arrayOf(classification),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(NoteSummary(
                        id = c.getString(0),
                        title = c.getString(1),
                        pinned = c.getInt(2) != 0,
                        createdAt = c.getLong(3),
                        updatedAt = c.getLong(4),
                    ))
                }
            }
        }

    fun createEncrypted(title: String, classification: String, encryptedDataId: String): String {
        val id = com.typedefai.cryptowl.crypto.RandomUtil.generateUUID()
        CryptoLog.d(C, "createEncrypted: noteId=$id classification=$classification encryptedDataId=$encryptedDataId title='$title'")
        val now = System.currentTimeMillis()
        db.execSQL(
            """INSERT INTO t_note (id, classification, title, content, pinned, encrypted_data_id, created_at, updated_at)
               VALUES (?, ?, ?, NULL, 0, ?, ?, ?)""",
            arrayOf<Any>(id, classification, title, encryptedDataId, now, now),
        )
        return id
    }

    /** Metadata of an encrypted (S/T) note. */
    data class EncryptedRef(
        val title: String,
        val encryptedDataId: String,
        val createdAt: Long,
        val updatedAt: Long,
    )

    /** Title + encrypted_data_id + timestamps for an encrypted note. */
    fun encryptedRef(id: String): EncryptedRef? =
        db.rawQuery(
            """SELECT title, encrypted_data_id, created_at, updated_at FROM t_note
               WHERE id = ? AND deleted_at IS NULL AND encrypted_data_id IS NOT NULL""",
            arrayOf(id),
        ).use { c ->
            if (c.moveToFirst()) EncryptedRef(
                title = c.getString(0),
                encryptedDataId = c.getString(1),
                createdAt = c.getLong(2),
                updatedAt = c.getLong(3),
            ) else null
        }

    fun touchTitle(id: String, title: String) {
        db.execSQL(
            "UPDATE t_note SET title = ?, updated_at = ? WHERE id = ?",
            arrayOf<Any>(title, System.currentTimeMillis(), id),
        )
    }
}
