package com.typedefai.cryptowl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.typedefai.cryptowl.LanguageButton
import com.typedefai.cryptowl.R
import com.typedefai.cryptowl.vault.Cwo1
import com.typedefai.cryptowl.vault.MomentCard
import com.typedefai.cryptowl.vault.MomentComment
import com.typedefai.cryptowl.vault.MomentMedia
import com.typedefai.cryptowl.vault.MomentPost
import com.typedefai.cryptowl.vault.MomentsRepository
import com.typedefai.cryptowl.vault.VaultSession
import com.typedefai.cryptowl.vault.VaultStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val PAGE_SIZE = 50

/** The moments timeline — WeChat-album layout, driven by the unlocked vault. */
@Composable
fun MomentsScreen(viewModel: MainViewModel, onLock: () -> Unit = viewModel::lockVault) {
    val context = LocalContext.current
    val session by viewModel.session.collectAsState()
    var posts by remember { mutableStateOf<List<MomentPost>>(emptyList()) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var viewerMedia by remember { mutableStateOf<MomentMedia?>(null) }

    suspend fun loadPage(openSession: VaultSession, offset: Int): TimelineLoad {
        val page = withContext(Dispatchers.IO) {
            MomentsRepository(openSession.db).timelinePage(PAGE_SIZE, offset)
        }
        return TimelineLoad(page.posts, page.hasMore)
    }

    LaunchedEffect(session) {
        loaded = false
        val openSession = session
        if (openSession == null) {
            posts = emptyList()
            hasMore = false
            loaded = true
        } else {
            loading = true
            try {
                val first = loadPage(openSession, 0)
                posts = first.posts
                hasMore = first.hasMore
            } finally {
                loading = false
                loaded = true
            }
        }
    }

    // Full-size viewer overlay (kept above the timeline UI).
    viewerMedia?.let { media ->
        val openSession = session
        if (openSession != null) {
            MediaViewer(
                media = media,
                session = openSession,
                onDismiss = { viewerMedia = null },
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.moments_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onLock) {
                Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.moments_lock))
            }
            LanguageButton()
        }
        if (session == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.home_vault_locked), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (loaded && posts.isEmpty()) {
            EmptyMoments()
        } else {
            val openSession = session ?: return@Column
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(posts, key = { it.id }) { post ->
                    MomentPostView(
                        post = post,
                        session = openSession,
                        context = context,
                        onOpenMedia = { viewerMedia = it },
                    )
                }
                if (hasMore) {
                    item(key = "load-more") {
                        LaunchedEffect(posts.size) {
                            if (loading) return@LaunchedEffect
                            loading = true
                            try {
                                val next = loadPage(openSession, posts.size)
                                posts = posts + next.posts
                                hasMore = next.hasMore
                            } finally {
                                loading = false
                            }
                        }
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.height(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

private data class TimelineLoad(val posts: List<MomentPost>, val hasMore: Boolean)

@Composable
private fun EmptyMoments() {
    Box(Modifier.fillMaxSize().padding(horizontal = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.moments_empty),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.moments_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ----------------------------------------------------------------------- post

@Composable
private fun MomentPostView(
    post: MomentPost,
    session: VaultSession,
    context: Context,
    onOpenMedia: (MomentMedia) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = post.authorName?.take(1) ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = post.authorName ?: post.authorUsername ?: stringResource(R.string.moments_unknown_author),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = formatTime(post.sourceCreatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        post.content?.takeIf { it.isNotBlank() }?.let { content ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 50.dp),
            )
        }

        post.location?.let { loc ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = poiName(loc),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 50.dp),
            )
        }

        if (post.media.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            MediaGrid(post.media, session, context, onOpenMedia, modifier = Modifier.padding(start = 50.dp))
        }

        post.cards.forEach { card ->
            Spacer(modifier = Modifier.height(8.dp))
            CardView(card, session, context, modifier = Modifier.padding(start = 50.dp))
        }

        if (post.likes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.padding(start = 50.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("❤", color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = post.likes.joinToString("、") { it.authorName ?: it.authorUsername ?: "" },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (post.comments.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                modifier = Modifier.padding(start = 50.dp).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    post.comments.forEach { comment -> CommentRow(comment) }
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun CommentRow(comment: MomentComment) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        if (comment.parentId != null) Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "${comment.authorName ?: comment.authorUsername ?: "?"}: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = comment.content ?: "",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun HorizontalDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    )
}

// ---------------------------------------------------------------------- media

@Composable
private fun MediaGrid(
    media: List<MomentMedia>,
    session: VaultSession,
    context: Context,
    onOpenMedia: (MomentMedia) -> Unit,
    modifier: Modifier,
) {
    val columns = if (media.size == 1) 1 else 3
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        media.chunked(columns).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowItems.forEach { m ->
                    MediaThumb(m, session, context, onOpenMedia, Modifier.weight(1f))
                }
                repeat(columns - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MediaThumb(
    media: MomentMedia,
    session: VaultSession,
    context: Context,
    onOpenMedia: (MomentMedia) -> Unit,
    modifier: Modifier,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, media.id, session) {
        value = withContext(Dispatchers.IO) { MomentsMediaLoader.loadThumbnail(context, session, media) }
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onOpenMedia(media) },
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = media.originalName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (media.mediaType == "video") {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun CardView(card: MomentCard, session: VaultSession, context: Context, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, card.id, session) {
        value = withContext(Dispatchers.IO) {
            card.thumbFilename?.let { MomentsMediaLoader.loadCardCover(context, session, card.id, it) }
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val cover = bitmap
            if (cover != null) {
                Image(
                    bitmap = cover,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                card.title?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
                card.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                card.sourceName?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- media loading

object MomentsMediaLoader {

    /** In-memory cache of decrypted thumbnails — a re-scroll must not re-decrypt. */
    private val cache = object : LruCache<String, Bitmap>(maxCacheEntries()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun maxCacheEntries(): Int {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return maxKb / 8
    }

    fun loadThumbnail(context: Context, session: VaultSession, media: MomentMedia): ImageBitmap? {
        val name = media.thumbnailFilename ?: return null
        return loadCwoImage(context, session, subdir = "thumbnails", filename = name, aad = media.id)
    }

    fun loadOriginal(context: Context, session: VaultSession, media: MomentMedia): ImageBitmap? =
        loadCwoImage(context, session, subdir = "attachments", filename = media.filename, aad = media.id)

    fun loadCardCover(context: Context, session: VaultSession, cardId: String, filename: String): ImageBitmap? =
        loadCwoImage(context, session, subdir = "attachments", filename = filename, aad = cardId)

    private fun loadCwoImage(context: Context, session: VaultSession, subdir: String, filename: String, aad: String): ImageBitmap? {
        val cacheKey = "${session.vaultId}/$subdir/$filename"
        cache.get(cacheKey)?.let { return it.asImageBitmap() }
        val file = File(File(VaultStore.vaultDir(context, session.vaultId), subdir), filename)
        if (!file.exists()) return null
        val bytes = Cwo1.decryptWholeFile(session.fek, aad.toByteArray(Charsets.UTF_8), file.readBytes())
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        bytes.fill(0)
        cache.put(cacheKey, bitmap)
        return bitmap.asImageBitmap()
    }
}

// ------------------------------------------------------------------ viewer

/** Full-screen decrypted original of a media item (images; videos later). */
@Composable
private fun MediaViewer(media: MomentMedia, session: VaultSession, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, media.id, session) {
        if (media.mediaType == "image") {
            value = withContext(Dispatchers.IO) { MomentsMediaLoader.loadOriginal(context, session, media) }
        }
    }
    LaunchedEffect(media.mediaType) {
        if (media.mediaType != "image") {
            Toast.makeText(context, R.string.viewer_video_unsupported, Toast.LENGTH_SHORT).show()
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.9f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(4.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.viewer_close),
                    tint = androidx.compose.ui.graphics.Color.White,
                )
            }
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = media.originalName,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.height(24.dp),
                    strokeWidth = 2.dp,
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
    }
}

// -------------------------------------------------------------------- helpers

private fun formatTime(epochMs: Long?): String {
    if (epochMs == null) return ""
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
}

private fun poiName(locationJson: String): String = try {
    val poi = JSONObject(locationJson).optString("poi_name")
    if (poi.isNotBlank()) "📍 $poi" else locationJson
} catch (e: Exception) {
    locationJson
}
