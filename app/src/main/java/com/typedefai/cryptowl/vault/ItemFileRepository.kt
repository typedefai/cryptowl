package com.typedefai.cryptowl.vault

import com.typedefai.cryptowl.crypto.CryptoLog
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.crypto.RandomUtil
import java.io.File

/** `t_file.storage` (docs/items.md "File storage: embedded by default"). */
enum class FileStorage(val code: String) {
    EMBEDDED("embedded"),
    EXTERNAL("external");

    companion object {
        fun fromCode(code: String): FileStorage =
            entries.firstOrNull { it.code == code } ?: EMBEDDED
    }
}

data class FileRecord(
    val id: String,
    val itemId: String,
    val name: String?,
    val mimeType: String?,
    val sizeBytes: Long,
    val storage: FileStorage,
    val externalName: String?,
    val content: ByteArray?,
    val thumbnail: ByteArray?,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * `t_file` repository — port of `cryptowl-desktop/core/vault/FileRepository.cpp`.
 *
 * C-tier files use the FEK (`HKDF(VaultKey, "", "file")`). Small files are
 * embedded as whole-file envelopes in `t_file.content`; larger ones are CWO1
 * chunked envelopes in `<vault>/attachments/<file_id>` for random access.
 * The storage choice is made once at import time; readers branch on it.
 */
class ItemFileRepository(
    private val db: SqlCipherDb,
    private val attachmentsDir: File,
    private val fek: ProtectedValue,
    private val embedMaxBytes: Long = VaultMetaJson.DEFAULT_EMBED_MAX_BYTES,
) {

    // ---------------------------------------------------------------- import

    fun importBytes(
        itemId: String,
        name: String?,
        mimeType: String?,
        bytes: ByteArray,
        thumbnailPlaintext: ByteArray? = null,
    ): String {
        val id = RandomUtil.generateUUID()
        val storage = if (bytes.size.toLong() <= embedMaxBytes) {
            FileStorage.EMBEDDED
        } else {
            FileStorage.EXTERNAL
        }

        val thumbnail = thumbnailPlaintext?.takeIf { it.isNotEmpty() }?.let {
            FileEnvelope.encryptEmbedded(fek, thumbAad(id), it)
        }

        var externalFile: File? = null
        val content = if (storage == FileStorage.EMBEDDED) {
            FileEnvelope.encryptEmbedded(fek, id.toByteArray(Charsets.UTF_8), bytes)
        } else {
            attachmentsDir.mkdirs()
            val temp = File(attachmentsDir, "$id.tmp")
            val target = File(attachmentsDir, id)
            temp.writeBytes(bytes)
            try {
                Cwo1.encryptChunkedFile(fek, id.toByteArray(Charsets.UTF_8), temp, target)
            } finally {
                temp.delete()
            }
            externalFile = target
            null
        }

        return try {
            insert(
                FileRecord(
                    id = id,
                    itemId = itemId,
                    name = name,
                    mimeType = mimeType,
                    sizeBytes = bytes.size.toLong(),
                    storage = storage,
                    externalName = if (storage == FileStorage.EXTERNAL) id else null,
                    content = content,
                    thumbnail = thumbnail,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            id
        } catch (t: Throwable) {
            externalFile?.delete()
            throw t
        }
    }

    /** Imports a file by streaming (large videos never sit in memory whole). */
    fun importFile(
        itemId: String,
        name: String?,
        mimeType: String?,
        source: File,
        thumbnailPlaintext: ByteArray? = null,
    ): String {
        val size = source.length()
        if (size <= embedMaxBytes) {
            return importBytes(itemId, name, mimeType, source.readBytes(), thumbnailPlaintext)
        }

        val id = RandomUtil.generateUUID()
        val thumbnail = thumbnailPlaintext?.takeIf { it.isNotEmpty() }?.let {
            FileEnvelope.encryptEmbedded(fek, thumbAad(id), it)
        }
        attachmentsDir.mkdirs()
        val temp = File(attachmentsDir, "$id.tmp")
        val target = File(attachmentsDir, id)
        try {
            Cwo1.encryptChunkedFile(fek, id.toByteArray(Charsets.UTF_8), source, temp)
            if (!temp.renameTo(target)) {
                temp.delete()
                error("could not finalize encrypted file")
            }
            insert(
                FileRecord(
                    id = id,
                    itemId = itemId,
                    name = name,
                    mimeType = mimeType,
                    sizeBytes = size,
                    storage = FileStorage.EXTERNAL,
                    externalName = id,
                    content = null,
                    thumbnail = thumbnail,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            return id
        } catch (t: Throwable) {
            temp.delete()
            target.delete()
            throw t
        }
    }

    // ------------------------------------------------------------------ read

    fun listForItem(itemId: String): List<FileRecord> = db.rawQuery(
        "SELECT id, item_id, name, mime_type, size_bytes, storage, external_name, " +
            "       thumbnail, created_at, updated_at " +
            "FROM t_file WHERE item_id = ? AND deleted_at IS NULL " +
            "ORDER BY created_at, id",
        arrayOf<Any?>(itemId),
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    FileRecord(
                        id = c.getString(0),
                        itemId = c.getString(1),
                        name = if (c.isNull(2)) null else c.getString(2),
                        mimeType = if (c.isNull(3)) null else c.getString(3),
                        sizeBytes = c.getLong(4),
                        storage = FileStorage.fromCode(c.getString(5)),
                        externalName = if (c.isNull(6)) null else c.getString(6),
                        content = null,
                        thumbnail = if (c.isNull(7)) null else c.getBlob(7),
                        createdAt = c.getLong(8),
                        updatedAt = c.getLong(9),
                    ),
                )
            }
        }
    }

    fun get(id: String): FileRecord? = db.rawQuery(
        "SELECT id, item_id, name, mime_type, size_bytes, storage, external_name, " +
            "       content, thumbnail, created_at, updated_at " +
            "FROM t_file WHERE id = ? AND deleted_at IS NULL",
        arrayOf<Any?>(id),
    ).use { c ->
        if (!c.moveToFirst()) null
        else FileRecord(
            id = c.getString(0),
            itemId = c.getString(1),
            name = if (c.isNull(2)) null else c.getString(2),
            mimeType = if (c.isNull(3)) null else c.getString(3),
            sizeBytes = c.getLong(4),
            storage = FileStorage.fromCode(c.getString(5)),
            externalName = if (c.isNull(6)) null else c.getString(6),
            content = if (c.isNull(7)) null else c.getBlob(7),
            thumbnail = if (c.isNull(8)) null else c.getBlob(8),
            createdAt = c.getLong(9),
            updatedAt = c.getLong(10),
        )
    }

    fun softDelete(id: String) {
        val now = System.currentTimeMillis()
        db.execSQL(
            "UPDATE t_file SET deleted_at = ?, updated_at = ? WHERE id = ? AND deleted_at IS NULL",
            arrayOf<Any?>(now, now, id),
        )
    }

    fun decryptThumbnail(file: FileRecord): ByteArray? {
        val thumbnail = file.thumbnail ?: return null
        return FileEnvelope.decryptEmbedded(fek, thumbAad(file.id), thumbnail)
    }

    /** Full plaintext of a file (embedded or chunked). */
    fun readAll(id: String): ByteArray {
        val file = get(id) ?: error("file was not found: $id")
        return when (file.storage) {
            FileStorage.EMBEDDED -> FileEnvelope.decryptEmbedded(
                fek,
                file.id.toByteArray(Charsets.UTF_8),
                file.content ?: ByteArray(0),
            )
            FileStorage.EXTERNAL -> Cwo1.decryptChunked(
                fek,
                file.id.toByteArray(Charsets.UTF_8),
                externalFile(file).readBytes(),
            )
        }
    }

    /** Decrypts an external CWO1 file to [target] (playback bridge). */
    fun decryptToFile(id: String, target: File) {
        val file = get(id) ?: error("file was not found: $id")
        when (file.storage) {
            FileStorage.EMBEDDED -> target.writeBytes(readAll(id))
            FileStorage.EXTERNAL -> Cwo1.decryptChunkedToFile(
                fek,
                file.id.toByteArray(Charsets.UTF_8),
                externalFile(file),
                target,
            )
        }
    }

    fun externalFile(file: FileRecord): File =
        File(attachmentsDir, file.externalName ?: file.id)

    // ---------------------------------------------------------------- helpers

    private fun insert(file: FileRecord) {
        db.execSQL(
            "INSERT INTO t_file " +
                "(id, item_id, name, mime_type, size_bytes, storage, content, thumbnail, " +
                " external_name, created_at, updated_at, deleted_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)",
            arrayOf<Any?>(
                file.id,
                file.itemId,
                file.name,
                file.mimeType,
                file.sizeBytes,
                file.storage.code,
                file.content,
                file.thumbnail,
                file.externalName,
                file.createdAt,
                file.updatedAt,
            ),
        )
        CryptoLog.d(C, "import: file=${file.id} item=${file.itemId} size=${file.sizeBytes} storage=${file.storage.code}")
    }

    private fun thumbAad(fileId: String): ByteArray = "$fileId:thumb".toByteArray(Charsets.UTF_8)

    private companion object {
        const val C = "ItemFileRepository"
    }
}
