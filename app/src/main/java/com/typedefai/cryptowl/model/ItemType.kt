package com.typedefai.cryptowl.model

/**
 * Built-in item types (docs/items.md "Type registry (plugins)").
 *
 * The type is a plain `t_item.type` string — new plugins do not need schema
 * changes. [Folder] is a container with no payload.
 */
enum class ItemType(val id: String, val defaultClassification: Classification) {
    PLAIN("plain", Classification.C),
    PASSWORD("password", Classification.S),
    PHOTO("photo", Classification.C),
    VIDEO("video", Classification.C),
    FOLDER("folder", Classification.C);

    companion object {
        fun fromId(id: String): ItemType = entries.firstOrNull { it.id == id } ?: PLAIN

        val contentTypes = listOf(PLAIN, PASSWORD)
        val mediaTypes = listOf(PHOTO, VIDEO)
    }
}

/** Sidebar/list filters (type is an attribute, not a location). */
enum class ItemFilter(val typeId: String?) {
    ALL(null),
    PLAIN(ItemType.PLAIN.id),
    PASSWORD(ItemType.PASSWORD.id),
    PHOTO(ItemType.PHOTO.id),
    VIDEO(ItemType.VIDEO.id),
    FOLDER(ItemType.FOLDER.id),
}
