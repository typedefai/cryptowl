package com.typedefai.cryptowl.model

/**
 * Unified item model — vault format v3 (docs/items.md).
 *
 * Every note, password, photo, video and folder is a `t_item` row; `type`
 * selects the plugin that renders/edits it. Tier semantics live in
 * `classification`: C payloads sit in `t_item.content` (SQLCipher boundary),
 * S/T payloads live in `t_encrypted_data` behind a per-item DEK.
 */
enum class Classification(val code: String) {
    C("C"), S("S"), T("T");

    val isSecret: Boolean get() = this != C

    companion object {
        fun fromCode(code: String): Classification =
            entries.firstOrNull { it.code == code }
                ?: error("unsupported classification: $code")

        fun fromCodeOrNull(code: String): Classification? =
            entries.firstOrNull { it.code == code }
    }
}

/** Row shown in lists: no payload, no secrets. */
data class ItemSummary(
    val id: String,
    val type: String,
    val title: String,
    val classification: Classification,
    val meta: String,
    val pinned: Boolean,
    val updatedAt: Long,
)

/** A fully loaded item (payload only present for C tier or after decryption). */
data class ItemRecord(
    val id: String,
    val type: String,
    val title: String,
    val classification: Classification,
    val parentId: String?,
    val meta: String,
    val payload: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class NewItem(
    val type: String,
    val classification: Classification,
    val title: String,
    val meta: String = "",
    val payload: String = "",
    val parentId: String? = null,
)

data class ItemUpdate(
    val title: String,
    val classification: Classification,
    val meta: String = "",
    val payload: String = "",
)

data class ItemVersionSummary(
    val seq: Int,
    val classification: Classification,
    val title: String,
    val meta: String,
    val message: String?,
    val fingerprint: String,
    val createdAt: Long,
)

data class ItemVersionRecord(
    val summary: ItemVersionSummary,
    val payload: String,
) {
    val seq: Int get() = summary.seq
    val classification: Classification get() = summary.classification
    val title: String get() = summary.title
    val meta: String get() = summary.meta
}
