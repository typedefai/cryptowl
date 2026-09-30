package com.typedefai.cryptowl.vault

import net.zetetic.database.sqlcipher.SQLiteDatabase

/** One vault media item (`t_file` row; C tier). */
data class MediaItem(
    val id: String,
    val storageName: String,
    val originalName: String?,
    val mimeType: String?,
    val sizeBytes: Long,
    val createdAt: Long,
)

/** One page of the media grid. */
data class MediaPage(
    val items: List<MediaItem>,
    val hasMore: Boolean,
)

/**
 * Raw SQLCipher access to `t_file` for the Confidential-tier media vault.
 * C-tier files use the FEK (no `t_data_encrypt_key` rows): `dek_id` stays NULL
 * and `classification = 'C'` (docs/design.md "File Encryption by Tier").
 */
class MediaRepository(private val db: SQLiteDatabase) {

    fun page(limit: Int, offset: Int = 0): MediaPage {
        val items = db.rawQuery(
            """SELECT id, storage_name, original_name, mime_type, size_bytes, created_at
               FROM t_file
               WHERE deleted_at IS NULL AND classification = 'C'
               ORDER BY created_at DESC, id DESC
               LIMIT ${limit + 1} OFFSET $offset""",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(MediaItem(
                        id = c.getString(0),
                        storageName = c.getString(1),
                        originalName = c.getString(2),
                        mimeType = c.getString(3),
                        sizeBytes = c.getLong(4),
                        createdAt = c.getLong(5),
                    ))
                }
            }
        }
        val hasMore = items.size > limit
        return MediaPage(if (hasMore) items.subList(0, limit) else items, hasMore)
    }

    fun get(id: String): MediaItem? =
        db.rawQuery(
            """SELECT id, storage_name, original_name, mime_type, size_bytes, created_at
               FROM t_file WHERE id = ? AND deleted_at IS NULL""",
            arrayOf(id),
        ).use { c ->
            if (c.moveToFirst()) MediaItem(
                id = c.getString(0),
                storageName = c.getString(1),
                originalName = c.getString(2),
                mimeType = c.getString(3),
                sizeBytes = c.getLong(4),
                createdAt = c.getLong(5),
            ) else null
        }

    fun insert(item: MediaItem) {
        val values = android.content.ContentValues().apply {
            put("id", item.id)
            putNull("dek_id")
            put("classification", "C")
            put("storage_name", item.storageName)
            put("original_name", item.originalName)
            put("mime_type", item.mimeType)
            put("size_bytes", item.sizeBytes)
            put("created_at", item.createdAt)
            put("updated_at", item.createdAt)
        }
        db.insert("t_file", null, values)
    }

    fun softDelete(id: String) {
        val now = System.currentTimeMillis()
        db.execSQL("UPDATE t_file SET deleted_at = ?, updated_at = ? WHERE id = ?", arrayOf<Any>(now, now, id))
    }
}
