package com.typedefai.cryptowl.vault

import net.zetetic.database.sqlcipher.SQLiteDatabase

/** Read model for the moments timeline (docs/moments.md §3). */
data class MomentPost(
    val id: String,
    val type: String,
    val authorName: String?,
    val authorUsername: String?,
    val content: String?,
    val location: String?,
    val visibility: String,
    val sourceCreatedAt: Long?,
    val likeCount: Int,
    val commentCount: Int,
    val cards: List<MomentCard> = emptyList(),
    val media: List<MomentMedia> = emptyList(),
    val comments: List<MomentComment> = emptyList(),
    val likes: List<MomentLike> = emptyList(),
) {
    val isPrivate: Boolean get() = visibility == "private"
}

data class MomentCard(
    val id: String,
    val cardType: String,
    val title: String?,
    val description: String?,
    val sourceName: String?,
    val url: String?,
    val thumbFilename: String?,
    val authorName: String?,
)

data class MomentMedia(
    val id: String,
    val mediaType: String,
    val filename: String,
    val originalName: String?,
    val mimeType: String?,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val thumbnailFilename: String?,
    val sortOrder: Int,
)

data class MomentComment(
    val id: String,
    val parentId: String?,
    val authorName: String?,
    val authorUsername: String?,
    val content: String?,
    val createdAt: Long?,
)

data class MomentLike(
    val authorName: String?,
    val authorUsername: String?,
)

/** One page of the timeline plus whether more pages follow. */
data class TimelinePage(
    val posts: List<MomentPost>,
    val hasMore: Boolean,
)

/**
 * Read access to the moments feature tables inside an unlocked vault.
 * C tier: everything here is L0/L1 plaintext inside the SQLCipher DB.
 *
 * The timeline is paginated (an imported WeChat archive is thousands of
 * posts) and child rows (cards/media/comments/likes) are fetched in batched
 * IN queries per page instead of per post.
 */
class MomentsRepository(private val db: SQLiteDatabase) {

    /** Timeline: non-deleted moments, newest first (source timeline order). */
    fun timelinePage(limit: Int, offset: Int = 0): TimelinePage {
        val moments = db.rawQuery(
            """SELECT id, type, author_name, author_username, content, location,
                      visibility, source_created_at, like_count, comment_count
               FROM t_moment WHERE deleted_at IS NULL
               ORDER BY source_created_at DESC, created_at DESC, id DESC
               LIMIT ${limit + 1} OFFSET $offset""",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(MomentPost(
                        id = cursor.getString(0),
                        type = cursor.getString(1),
                        authorName = cursor.stringOrNull(2),
                        authorUsername = cursor.stringOrNull(3),
                        content = cursor.stringOrNull(4),
                        location = cursor.stringOrNull(5),
                        visibility = cursor.getString(6),
                        sourceCreatedAt = cursor.longOrNull(7),
                        likeCount = cursor.getInt(8),
                        commentCount = cursor.getInt(9),
                    ))
                }
            }
        }
        val hasMore = moments.size > limit
        val page = if (hasMore) moments.subList(0, limit) else moments
        val withChildren = attachChildren(page)
        return TimelinePage(withChildren, hasMore)
    }

    /** Full timeline (small vaults / tests). Pages internally. */
    fun timeline(): List<MomentPost> {
        val all = mutableListOf<MomentPost>()
        var offset = 0
        while (true) {
            val page = timelinePage(PAGE_SIZE, offset)
            all += page.posts
            if (!page.hasMore) return all
            offset += page.posts.size
        }
    }

    /** A single moment with its children. */
    fun moment(id: String): MomentPost? {
        val moment = db.rawQuery(
            """SELECT id, type, author_name, author_username, content, location,
                      visibility, source_created_at, like_count, comment_count
               FROM t_moment WHERE id = ? AND deleted_at IS NULL""",
            arrayOf(id),
        ).use { cursor ->
            if (cursor.moveToFirst()) MomentPost(
                id = cursor.getString(0),
                type = cursor.getString(1),
                authorName = cursor.stringOrNull(2),
                authorUsername = cursor.stringOrNull(3),
                content = cursor.stringOrNull(4),
                location = cursor.stringOrNull(5),
                visibility = cursor.getString(6),
                sourceCreatedAt = cursor.longOrNull(7),
                likeCount = cursor.getInt(8),
                commentCount = cursor.getInt(9),
            ) else null
        } ?: return null
        return attachChildren(listOf(moment)).single()
    }

    /** Fills cards/media/comments/likes for [posts] with 4 batched queries. */
    private fun attachChildren(posts: List<MomentPost>): List<MomentPost> {
        if (posts.isEmpty()) return posts
        val ids = posts.map { it.id }

        val cards = grouped(ids, CardsQuery) { c ->
            MomentCard(
                id = c.getString(0),
                cardType = c.getString(1),
                title = c.stringOrNull(2),
                description = c.stringOrNull(3),
                sourceName = c.stringOrNull(4),
                url = c.stringOrNull(5),
                thumbFilename = c.stringOrNull(6),
                authorName = c.stringOrNull(7),
            ) to c.getString(8)
        }

        val media = grouped(ids, MediaQuery) { c ->
            MomentMedia(
                id = c.getString(0),
                mediaType = c.getString(1),
                filename = c.getString(2),
                originalName = c.stringOrNull(3),
                mimeType = c.stringOrNull(4),
                width = c.intOrNull(5),
                height = c.intOrNull(6),
                durationMs = c.longOrNull(7),
                thumbnailFilename = c.stringOrNull(8),
                sortOrder = c.getInt(9),
            ) to c.getString(10)
        }

        val comments = grouped(ids, CommentsQuery) { c ->
            MomentComment(
                id = c.getString(0),
                parentId = c.stringOrNull(1),
                authorName = c.stringOrNull(2),
                authorUsername = c.stringOrNull(3),
                content = c.stringOrNull(4),
                createdAt = c.longOrNull(5),
            ) to c.getString(6)
        }

        val likes = grouped(ids, LikesQuery) { c ->
            MomentLike(
                authorName = c.stringOrNull(0),
                authorUsername = c.stringOrNull(1),
            ) to c.getString(2)
        }

        return posts.map { post ->
            post.copy(
                cards = cards[post.id].orEmpty(),
                media = media[post.id].orEmpty(),
                comments = comments[post.id].orEmpty(),
                likes = likes[post.id].orEmpty(),
            )
        }
    }

    /**
     * SELECT ... WHERE moment_id IN (...) for [ids]; [row] returns the mapped
     * row plus its moment_id group key. Ordering matches the per-table sort.
     */
    private fun <T> grouped(ids: List<String>, sql: String, row: (android.database.Cursor) -> Pair<T, String>): Map<String, List<T>> {
        val placeholders = ids.joinToString(",") { "?" }
        return db.rawQuery(sql.replace("__IN__", placeholders), ids.toTypedArray()).use { c ->
            val out = mutableMapOf<String, MutableList<T>>()
            while (c.moveToNext()) {
                val (value, key) = row(c)
                out.getOrPut(key) { mutableListOf() }.add(value)
            }
            out
        }
    }

    private companion object {
        const val PAGE_SIZE = 50
        const val CardsQuery = """SELECT id, card_type, title, description, source_name, url,
                     thumb_filename, author_name, moment_id
              FROM t_moment_card WHERE deleted_at IS NULL AND moment_id IN (__IN__)
              ORDER BY created_at"""
        const val MediaQuery = """SELECT id, media_type, filename, original_name, mime_type, width,
                     height, duration_ms, thumbnail_filename, sort_order, moment_id
              FROM t_moment_media WHERE deleted_at IS NULL AND moment_id IN (__IN__)
              ORDER BY sort_order"""
        const val CommentsQuery = """SELECT id, parent_id, author_name, author_username, content,
                     created_at, moment_id
              FROM t_moment_comment WHERE deleted_at IS NULL AND moment_id IN (__IN__)
              ORDER BY created_at"""
        const val LikesQuery = """SELECT author_name, author_username, moment_id
              FROM t_moment_like WHERE moment_id IN (__IN__)"""
    }

    private fun android.database.Cursor.stringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private fun android.database.Cursor.longOrNull(index: Int): Long? =
        if (isNull(index)) null else getLong(index)

    private fun android.database.Cursor.intOrNull(index: Int): Int? =
        if (isNull(index)) null else getInt(index)
}
