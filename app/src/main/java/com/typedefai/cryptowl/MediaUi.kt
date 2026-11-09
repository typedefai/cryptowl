package com.typedefai.cryptowl

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.typedefai.cryptowl.media.VideoFilePlayer
import com.typedefai.cryptowl.ui.components.EmptyState
import com.typedefai.cryptowl.vault.MediaEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Confidential-tier media: a thumbnail grid over photo/video items. Tap opens
 * the decrypted original (image) or decrypts to a cache temp file for playback
 * (video), which is deleted when the viewer closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(viewModel: MediaViewModel, onLock: () -> Unit) {
    val entries by viewModel.entries.collectAsState()
    val errorMessage by viewModel.error.collectAsState()
    val context = LocalContext.current
    var viewer by remember { mutableStateOf<MediaUiEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<MediaUiEntry?>(null) }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val (name, mime) = queryNameAndMime(context.contentResolver, uri)
        viewModel.importUri(uri, name, mime, isVideo = mime?.startsWith("video/") == true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.media_title)) },
                actions = {
                    IconButton(onClick = onLock) {
                        Icon(Icons.Outlined.Lock, contentDescription = stringResource(R.string.settings_lock_now))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.media_import))
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (entries.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = stringResource(R.string.media_empty),
                    hint = stringResource(R.string.media_empty_hint),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(entries, key = { it.entry.item.id }) { media ->
                        MediaTile(media, onClick = { viewer = media }, onLongClick = { deleteTarget = media })
                    }
                }
            }
            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
            }
        }
    }

    viewer?.let { media ->
        MediaViewer(media = media, viewModel = viewModel, onDismiss = { viewer = null })
    }

    deleteTarget?.let { media ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.media_delete_title)) },
            text = { Text(stringResource(R.string.media_delete_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(media.entry)
                    deleteTarget = null
                }) { Text(stringResource(R.string.media_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(media: MediaUiEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(120.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        val thumbnail = media.thumbnail
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = media.entry.item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    if (media.entry.item.type == "video") Icons.Outlined.Videocam else Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Text(
            text = media.entry.item.title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(4.dp),
        )
    }
}

@Composable
private fun MediaViewer(media: MediaUiEntry, viewModel: MediaViewModel, onDismiss: () -> Unit) {
    val isVideo = media.entry.item.type == "video"
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var videoFile by remember { mutableStateOf<File?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(media.entry.item.id) {
        withContext(Dispatchers.IO) {
            if (isVideo) {
                videoFile = viewModel.decryptToCache(media.entry)
            } else {
                imageBytes = viewModel.originalBytes(media.entry)
            }
            loading = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                loading -> CircularProgressIndicator()
                isVideo -> {
                    val file = videoFile
                    if (file != null) VideoFilePlayer(file = file, modifier = Modifier.fillMaxSize())
                    else Text(stringResource(R.string.media_error), color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    val bytes = imageBytes
                    val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = media.entry.item.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(stringResource(R.string.media_error), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.viewer_close))
            }
        }
    }
}

private fun queryNameAndMime(resolver: android.content.ContentResolver, uri: Uri): Pair<String, String?> {
    var name = "media"
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) name = c.getString(0) ?: name
    }
    val mime = resolver.getType(uri)
    return name to mime
}
