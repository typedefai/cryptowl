package com.typedefai.cryptowl.vault

import com.typedefai.cryptowl.crypto.AesGcm
import com.typedefai.cryptowl.crypto.AuthEncryptedData
import com.typedefai.cryptowl.crypto.CryptoLog
import com.typedefai.cryptowl.crypto.CrockfordBase32
import com.typedefai.cryptowl.crypto.HmacSha256
import com.typedefai.cryptowl.crypto.KdfService
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.crypto.RandomUtil
import com.typedefai.cryptowl.crypto.WrappedKey
import com.typedefai.cryptowl.model.Classification
import com.typedefai.cryptowl.model.ItemRecord
import com.typedefai.cryptowl.model.ItemSummary
import com.typedefai.cryptowl.model.ItemUpdate
import com.typedefai.cryptowl.model.ItemVersionRecord
import com.typedefai.cryptowl.model.ItemVersionSummary
import com.typedefai.cryptowl.model.NewItem

/**
 * `t_item` repository — a 1:1 port of the desktop reference
 * (`cryptowl-desktop/core/vault/ItemRepository.cpp`).
 *
 * Tier routing (docs/items.md "Payload routing"):
 * - **C**: payload plaintext in `t_item.content` (SQLCipher is the boundary).
 * - **S**: per-item DEK wrapped by the KEK (`t_data_encrypt_key`), payload in
 *   `t_encrypted_data`. The KEK is per-access (BioKey), so it is passed in.
 * - **T**: DEK wrapped by the TopSecretKEK (two-factor gate), also passed in.
 *
 * History semantics:
 * - `fingerprint = Crockford32(HMAC-SHA256(HFK, title \0 classification \0 payload))`.
 * - Create writes seq 1; an update writes seq+1 unless the fingerprint equals
 *   the latest version (an unchanged save writes no row and does not bump
 *   `updated_at` — desktop parity).
 * - Restore reads the snapshot and writes it as a new version.
 */
class ItemRepository(
    private val db: SqlCipherDb,
    private val hfk: ProtectedValue,
    private val kdf: KdfService = KdfService(),
) {

    // ------------------------------------------------------------------ list

    fun list(type: String? = null): List<ItemSummary> {
        val sql = if (type.isNullOrEmpty()) {
            "SELECT id, type, title, classification, meta, pinned, updated_at " +
                "FROM t_item WHERE deleted_at IS NULL " +
                "ORDER BY updated_at DESC, title COLLATE NOCASE"
        } else {
            "SELECT id, type, title, classification, meta, pinned, updated_at " +
                "FROM t_item WHERE type = ? AND deleted_at IS NULL " +
                "ORDER BY updated_at DESC, title COLLATE NOCASE"
        }
        val args = if (type.isNullOrEmpty()) null else arrayOf<Any?>(type)
        return db.rawQuery(sql, args).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        ItemSummary(
                            id = c.getString(0),
                            type = c.getString(1),
                            title = c.getString(2),
                            classification = Classification.fromCode(c.getString(3)),
                            meta = c.getString(4),
                            pinned = c.getLong(5) != 0L,
                            updatedAt = c.getLong(6),
                        ),
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------- get

    /** Metadata only (never touches S/T payloads or keys). */
    fun getSummary(id: String): ItemSummary = db.rawQuery(
        "SELECT id, type, title, classification, meta, pinned, updated_at " +
            "FROM t_item WHERE id = ? AND deleted_at IS NULL",
        arrayOf<Any?>(id),
    ).use { c ->
        if (!c.moveToFirst()) error("item was not found: $id")
        ItemSummary(
            id = c.getString(0),
            type = c.getString(1),
            title = c.getString(2),
            classification = Classification.fromCode(c.getString(3)),
            meta = c.getString(4),
            pinned = c.getLong(5) != 0L,
            updatedAt = c.getLong(6),
        )
    }

    /** Reads an item; S requires [kek], T requires [topSecretKek]. */
    fun get(
        id: String,
        kek: ProtectedValue? = null,
        topSecretKek: ProtectedValue? = null,
    ): ItemRecord {
        val sql =
            "SELECT i.id, i.type, i.title, i.classification, i.parent_id, i.meta, " +
                "       i.created_at, i.updated_at, i.content, e.id, e.dek_id, e.content, " +
                "       e.nonce, e.auth_tag " +
                "FROM t_item i " +
                "LEFT JOIN t_encrypted_data e " +
                "  ON e.id = i.encrypted_data_id AND e.deleted_at IS NULL " +
                "WHERE i.id = ? AND i.deleted_at IS NULL"
        db.rawQuery(sql, arrayOf<Any?>(id)).use { c ->
            if (!c.moveToFirst()) error("item was not found: $id")
            val record = ItemRecord(
                id = c.getString(0),
                type = c.getString(1),
                title = c.getString(2),
                classification = Classification.fromCode(c.getString(3)),
                parentId = if (c.isNull(4)) null else c.getString(4),
                meta = c.getString(5),
                payload = "",
                createdAt = c.getLong(6),
                updatedAt = c.getLong(7),
            )
            val payload = when (record.classification) {
                Classification.C -> c.getString(8)
                Classification.S, Classification.T -> {
                    val topSecret = record.classification == Classification.T
                    val dataId = c.getString(9)
                    val dekId = c.getString(10)
                    val content = c.getBlob(11)
                    val nonce = c.getBlob(12)
                    val tag = c.getBlob(13)
                    require(dataId.isNotEmpty() && dekId.isNotEmpty() && nonce.size == 12 && tag.size == 16) {
                        "encrypted item data is invalid"
                    }
                    val wrappingKey = if (topSecret) {
                        topSecretKek ?: throw VaultLockedException("Top Secret content is locked")
                    } else {
                        kek ?: throw VaultLockedException("Secret content is locked")
                    }
                    decryptPayload(wrappingKey, topSecret, dekId, dataId, content, nonce, tag)
                }
            }
            return record.copy(payload = payload)
        }
    }

    // ---------------------------------------------------------------- create

    fun create(
        item: NewItem,
        kek: ProtectedValue? = null,
        topSecretKek: ProtectedValue? = null,
    ): String {
        require(item.type.isNotBlank()) { "item type is required" }
        require(item.title.isNotEmpty()) { "item title is required" }
        val secret = item.classification.isSecret
        val topSecret = item.classification == Classification.T
        val wrappingKey = when (item.classification) {
            Classification.C -> null
            Classification.S -> kek ?: throw VaultLockedException("Secret access is locked")
            Classification.T -> topSecretKek ?: throw VaultLockedException("Top Secret content is locked")
        }

        val itemId = RandomUtil.generateUUID()
        val timestamp = System.currentTimeMillis()
        var dekId: String? = null
        var dataId: String? = null
        var dek: ProtectedValue? = null
        var encrypted: EncryptedPayload? = null

        if (secret) {
            dekId = RandomUtil.generateUUID()
            dataId = RandomUtil.generateUUID()
            dek = ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(KEY_SIZE))
            encrypted = encryptPayload(dek, dataId, item.payload)
        }

        try {
            db.transaction {
                if (secret) {
                    insertDek(dekId!!, wrappingKey!!, dek!!, timestamp, topSecret)
                    insertEncryptedData(dataId!!, dekId, encrypted!!, timestamp)
                }
                insertItem(
                    itemId = itemId,
                    item = item,
                    content = if (secret) null else item.payload,
                    dataId = if (secret) dataId else null,
                    timestamp = timestamp,
                )
                insertVersion(
                    itemId = itemId,
                    seq = 1,
                    classification = item.classification.code,
                    title = item.title,
                    content = if (secret) null else item.payload,
                    dataId = if (secret) dataId else null,
                    meta = item.meta,
                    fingerprint = fingerprint(item.title, item.classification, item.payload),
                    timestamp = timestamp,
                )
            }
        } finally {
            dek?.clear()
        }
        CryptoLog.d(C, "create: id=$itemId type=${item.type} cls=${item.classification.code}")
        return itemId
    }

    // ---------------------------------------------------------------- update

    /** Returns true when a version row was written (false for unchanged saves). */
    fun update(
        id: String,
        update: ItemUpdate,
        kek: ProtectedValue? = null,
        topSecretKek: ProtectedValue? = null,
    ): Boolean {
        require(update.title.isNotEmpty()) { "item title is required" }

        var written = false
        db.transaction {
            val current = db.rawQuery(
                "SELECT i.classification, i.content, i.encrypted_data_id, e.dek_id " +
                    "FROM t_item i " +
                    "LEFT JOIN t_encrypted_data e " +
                    "  ON e.id = i.encrypted_data_id AND e.deleted_at IS NULL " +
                    "WHERE i.id = ? AND i.deleted_at IS NULL",
                arrayOf<Any?>(id),
            ).use { c ->
                if (!c.moveToFirst()) error("item was not found: $id")
                CurrentItem(
                    classification = Classification.fromCode(c.getString(0)),
                    encryptedDataId = if (c.isNull(2)) null else c.getString(2),
                    dekId = if (c.isNull(3)) null else c.getString(3),
                )
            }

            val desired = update.classification
            val currentCls = current.classification
            if (currentCls.isSecret && (current.encryptedDataId == null || current.dekId == null)) {
                error("encrypted item data is invalid")
            }
            val touchesTopSecret = currentCls == Classification.T || desired == Classification.T
            if (touchesTopSecret) {
                topSecretKek ?: throw VaultLockedException("Top Secret content is locked")
            }
            if (desired == Classification.S) {
                kek ?: throw VaultLockedException("Secret access is locked")
            }

            val newFingerprint = fingerprint(update.title, desired, update.payload)
            val latest = latestVersion(id)
            if (latest.first > 0 && latest.second == newFingerprint) {
                CryptoLog.d(C, "update: item unchanged; no version written ($id)")
                return@transaction
            }

            val timestamp = System.currentTimeMillis()
            var newDekId: String? = null
            var newDataId: String? = null
            var dek: ProtectedValue? = null
            var encrypted: EncryptedPayload? = null

            if (desired.isSecret) {
                if (currentCls == Classification.S && desired == Classification.S) {
                    newDekId = current.dekId
                    dek = kdf.unwrapKey(
                        wrapped = loadDekRow(newDekId!!),
                        wrappingKey = kek!!,
                        aad = newDekId.toByteArray(Charsets.UTF_8),
                    )
                } else {
                    newDekId = RandomUtil.generateUUID()
                    dek = ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(KEY_SIZE))
                    val wrappingKey = if (desired == Classification.T) topSecretKek!! else kek!!
                    insertDek(newDekId, wrappingKey, dek, timestamp, desired == Classification.T)
                }
                newDataId = RandomUtil.generateUUID()
                encrypted = encryptPayload(dek, newDataId, update.payload)
                insertEncryptedData(newDataId, newDekId, encrypted, timestamp)
            }

            val content = if (desired.isSecret) null else update.payload
            db.execSQL(
                "UPDATE t_item SET title = ?, classification = ?, content = ?, " +
                    "encrypted_data_id = ?, meta = ?, updated_at = ? " +
                    "WHERE id = ? AND deleted_at IS NULL",
                arrayOf<Any?>(
                    update.title,
                    desired.code,
                    content,
                    if (desired.isSecret) newDataId else null,
                    update.meta,
                    timestamp,
                    id,
                ),
            )
            check(db.changes == 1) { "could not update item" }

            insertVersion(
                itemId = id,
                seq = latest.first + 1,
                classification = desired.code,
                title = update.title,
                content = content,
                dataId = if (desired.isSecret) newDataId else null,
                meta = update.meta,
                fingerprint = newFingerprint,
                timestamp = timestamp,
            )
            dek?.clear()
            written = true
        }
        return written
    }

    // ------------------------------------------------------------ soft delete

    fun softDelete(id: String) {
        val now = System.currentTimeMillis()
        db.execSQL(
            "UPDATE t_item SET deleted_at = ?, updated_at = ? WHERE id = ? AND deleted_at IS NULL",
            arrayOf<Any?>(now, now, id),
        )
        check(db.changes == 1) { "item was not found: $id" }
        CryptoLog.d(C, "softDelete: id=$id")
    }

    // --------------------------------------------------------------- history

    fun versions(id: String): List<ItemVersionSummary> = db.rawQuery(
        "SELECT seq, classification, title, meta, message, fingerprint, created_at " +
            "FROM t_item_version WHERE item_id = ? ORDER BY seq DESC",
        arrayOf<Any?>(id),
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    ItemVersionSummary(
                        seq = c.getInt(0),
                        classification = Classification.fromCode(c.getString(1)),
                        title = c.getString(2),
                        meta = c.getString(3),
                        message = if (c.isNull(4)) null else c.getString(4),
                        fingerprint = c.getString(5),
                        createdAt = c.getLong(6),
                    ),
                )
            }
        }
    }

    /** Reads a snapshot; S/T snapshots require the matching per-access key. */
    fun version(
        id: String,
        seq: Int,
        kek: ProtectedValue? = null,
        topSecretKek: ProtectedValue? = null,
    ): ItemVersionRecord {
        val sql =
            "SELECT v.seq, v.classification, v.title, v.meta, v.message, " +
                "       v.fingerprint, v.created_at, v.content, v.encrypted_data_id, " +
                "       e.dek_id, e.content, e.nonce, e.auth_tag " +
                "FROM t_item_version v " +
                "LEFT JOIN t_encrypted_data e ON e.id = v.encrypted_data_id " +
                "WHERE v.item_id = ? AND v.seq = ?"
        db.rawQuery(sql, arrayOf<Any?>(id, seq)).use { c ->
            if (!c.moveToFirst()) error("version was not found: $id/$seq")
            val summary = ItemVersionSummary(
                seq = c.getInt(0),
                classification = Classification.fromCode(c.getString(1)),
                title = c.getString(2),
                meta = c.getString(3),
                message = if (c.isNull(4)) null else c.getString(4),
                fingerprint = c.getString(5),
                createdAt = c.getLong(6),
            )
            val payload = when (summary.classification) {
                Classification.C -> c.getString(7)
                Classification.S, Classification.T -> {
                    val topSecret = summary.classification == Classification.T
                    val dataId = c.getString(8)
                    val dekId = c.getString(9)
                    val content = c.getBlob(10)
                    val nonce = c.getBlob(11)
                    val tag = c.getBlob(12)
                    require(dataId.isNotEmpty() && dekId.isNotEmpty() && nonce.size == 12 && tag.size == 16) {
                        "version encrypted data is invalid"
                    }
                    val wrappingKey = if (topSecret) {
                        topSecretKek ?: throw VaultLockedException("Top Secret version is locked")
                    } else {
                        kek ?: throw VaultLockedException("Secret version is locked")
                    }
                    decryptPayload(wrappingKey, topSecret, dekId, dataId, content, nonce, tag)
                }
            }
            return ItemVersionRecord(summary, payload)
        }
    }

    /** Restores a snapshot as a new version (history stays linear). */
    fun restoreVersion(
        id: String,
        seq: Int,
        kek: ProtectedValue? = null,
        topSecretKek: ProtectedValue? = null,
    ) {
        val version = version(id, seq, kek, topSecretKek)
        update(
            id = id,
            update = ItemUpdate(
                title = version.title,
                classification = version.classification,
                meta = version.meta,
                payload = version.payload,
            ),
            kek = kek,
            topSecretKek = topSecretKek,
        )
    }

    // ---------------------------------------------------------------- helpers

    private data class CurrentItem(
        val classification: Classification,
        val encryptedDataId: String?,
        val dekId: String?,
    )

    private data class EncryptedPayload(
        val content: ByteArray,
        val nonce: ByteArray,
        val tag: ByteArray,
    )

    /** Latest version: (seq, fingerprint); seq 0 when the item has none. */
    private fun latestVersion(itemId: String): Pair<Int, String?> = db.rawQuery(
        "SELECT seq, fingerprint FROM t_item_version WHERE item_id = ? ORDER BY seq DESC LIMIT 1",
        arrayOf<Any?>(itemId),
    ).use { c ->
        if (c.moveToNext()) c.getInt(0) to c.getString(1) else 0 to null
    }

    private fun fingerprint(title: String, classification: Classification, payload: String): String {
        val message = title.toByteArray(Charsets.UTF_8) +
            byteArrayOf(0) +
            classification.code.toByteArray(Charsets.UTF_8) +
            byteArrayOf(0) +
            payload.toByteArray(Charsets.UTF_8)
        val digest = hfk.use { HmacSha256.mac(it, message) }
        return CrockfordBase32.encode(digest)
    }

    private fun encryptPayload(
        dek: ProtectedValue,
        dataId: String,
        payload: String,
    ): EncryptedPayload {
        val nonce = RandomUtil.generateSecureBytes(AesGcm.NONCE_SIZE)
        val plain = payload.toByteArray(Charsets.UTF_8)
        try {
            val encrypted = dek.use { raw ->
                AesGcm.encrypt(raw, nonce, dataId.toByteArray(Charsets.UTF_8), plain)
            }
            return EncryptedPayload(encrypted.cipherText, nonce, encrypted.authTag)
        } finally {
            plain.fill(0)
        }
    }

    private fun decryptPayload(
        wrappingKey: ProtectedValue,
        topSecret: Boolean,
        dekId: String,
        dataId: String,
        content: ByteArray,
        nonce: ByteArray,
        tag: ByteArray,
    ): String {
        val dek = kdf.unwrapKey(
            wrapped = loadDekRow(dekId),
            wrappingKey = wrappingKey,
            aad = dekId.toByteArray(Charsets.UTF_8),
        )
        return try {
            val plain = dek.use { raw ->
                AesGcm.decrypt(
                    raw,
                    nonce,
                    dataId.toByteArray(Charsets.UTF_8),
                    AuthEncryptedData(content, tag),
                )
            }
            try {
                String(plain, Charsets.UTF_8)
            } finally {
                plain.fill(0)
            }
        } finally {
            dek.clear()
        }
    }

    private fun loadDekRow(dekId: String): WrappedKey = db.rawQuery(
        "SELECT algorithm, ciphertext, nonce, auth_tag FROM t_data_encrypt_key WHERE id = ?",
        arrayOf<Any?>(dekId),
    ).use { c ->
        if (!c.moveToFirst()) error("encrypted item key was not found: $dekId")
        check(c.getString(0) == "AES-256-GCM") { "unsupported item key algorithm" }
        val wrapped = WrappedKey(c.getBlob(1), c.getBlob(2), c.getBlob(3))
        require(wrapped.cipherText.size == 32 && wrapped.nonce.size == 12 && wrapped.authTag.size == 16) {
            "invalid wrapped item key"
        }
        wrapped
    }

    private fun insertDek(
        dekId: String,
        wrappingKey: ProtectedValue,
        dek: ProtectedValue,
        timestamp: Long,
        topSecret: Boolean,
    ) {
        val nonce = RandomUtil.generateSecureBytes(AesGcm.NONCE_SIZE)
        val wrapped = kdf.wrapKey(
            key = dek,
            wrappingKey = wrappingKey,
            aad = dekId.toByteArray(Charsets.UTF_8),
            nonce = nonce,
        )
        db.execSQL(
            "INSERT INTO t_data_encrypt_key " +
                "(id, algorithm, ciphertext, nonce, auth_tag, created_at, updated_at) " +
                "VALUES (?, 'AES-256-GCM', ?, ?, ?, ?, ?)",
            arrayOf<Any?>(dekId, wrapped.cipherText, wrapped.nonce, wrapped.authTag, timestamp, timestamp),
        )
        CryptoLog.d(C, "insertDek: id=$dekId topSecret=$topSecret wrapper=${if (topSecret) "top_secret_kek" else "kek"}")
    }

    private fun insertEncryptedData(
        dataId: String,
        dekId: String,
        encrypted: EncryptedPayload,
        timestamp: Long,
    ) {
        db.execSQL(
            "INSERT INTO t_encrypted_data " +
                "(id, dek_id, algorithm, content, nonce, auth_tag, created_at, updated_at) " +
                "VALUES (?, ?, 'AES-256-GCM', ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                dataId, dekId, encrypted.content, encrypted.nonce, encrypted.tag, timestamp, timestamp,
            ),
        )
    }

    private fun insertItem(
        itemId: String,
        item: NewItem,
        content: String?,
        dataId: String?,
        timestamp: Long,
    ) {
        db.execSQL(
            "INSERT INTO t_item " +
                "(id, type, title, classification, parent_id, pinned, content, " +
                " encrypted_data_id, meta, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                itemId,
                item.type,
                item.title,
                item.classification.code,
                item.parentId,
                content,
                dataId,
                item.meta,
                timestamp,
                timestamp,
            ),
        )
    }

    private fun insertVersion(
        itemId: String,
        seq: Int,
        classification: String,
        title: String,
        content: String?,
        dataId: String?,
        meta: String,
        fingerprint: String,
        timestamp: Long,
    ) {
        db.execSQL(
            "INSERT INTO t_item_version " +
                "(id, item_id, seq, classification, title, content, encrypted_data_id, " +
                " meta, message, fingerprint, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)",
            arrayOf<Any?>(
                RandomUtil.generateUUID(), itemId, seq, classification, title,
                content, dataId, meta, fingerprint, timestamp,
            ),
        )
    }

    private companion object {
        const val C = "ItemRepository"
        const val KEY_SIZE = 32
    }
}

/** Raised when an S/T operation needs a key that was not authorized. */
class VaultLockedException(message: String) : Exception(message)
