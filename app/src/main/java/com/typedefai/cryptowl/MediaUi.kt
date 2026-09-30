package com.typedefai.cryptowl

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.typedefai.cryptowl.media.Cwo1VideoPlayer
import com.typedefai.cryptowl.ui.MediaLoader
import com.typedefai.cryptowl.ui.components.EmptyState
import com.typedefai.cryptowl.vault.MediaItem
import com.typedefai.cryptowl.vault.VaultSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Confidential-tier media vault: encrypted photos/videos, import + grid. */
@Composable
fun MediaScreen(viewModel: MediaViewModel, session: VaultSession?) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val error by viewModel.error.collectAsState()
    var viewerItem by remember { mutableStateOf<MediaItem?>(null) }

    LaunchedEffect(Unit) { viewModel.clearError() }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) viewModel.import(uris)
    }

    viewerItem?.let { item ->
        if (session != null) {
            MediaItemViewer(
                item = item,
                session = session,
                viewModel = viewModel,
                onDismiss = { viewerItem = null },
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Text(
                text = stringResource(R.string.media_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
            )
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            when {
                entries.isEmpty() && importing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                entries.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = stringResource(R.string.media_empty),
                    hint = stringResource(R.string.media_empty_hint),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                else -> {
                    if (session != null) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            items(entries, key = { it.id }) { item ->
                                MediaThumb(
                                    item = item,
                                    session = session,
                                    context = context,
                                    onClick = { viewerItem = item },
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                pickMedia.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                )
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            if (importing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.media_import))
            }
        }
    }
}

@Composable
private fun MediaThumb(
    item: MediaItem,
    session: VaultSession,
    context: android.content.Context,
    onClick: () -> Unit,
) {
    val thumb by produceState<ImageBitmap?>(initialValue = null, item.id, session) {
        value = withContext(Dispatchers.IO) {
            MediaLoader.loadCwo(context, session, "thumbnails", "${item.id}_t.cwo", item.id)
        }
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val image = thumb
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = item.originalName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (item.mimeType?.startsWith("video/") == true) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** Full-screen viewer: decrypts the original image; videos show a play hint. */
@Composable
private fun MediaItemViewer(
    item: MediaItem,
    session: VaultSession,
    viewModel: MediaViewModel,
    onDismiss: () -> Unit,
) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val isVideo = item.mimeType?.startsWith("video/") == true

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.media_delete_title)) },
            text = { Text(stringResource(R.string.media_delete_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete(item.id)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.media_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            if (!isVideo) {
                val bitmap by produceState<ImageBitmap?>(initialValue = null, item.id, session) {
                    value = withContext(Dispatchers.IO) {
                        viewModel.loadOriginal(item)?.asImageBitmap()
                    }
                }
                val image = bitmap
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = item.originalName,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                }
            } else {
                Cwo1VideoPlayer(
                    session = session,
                    subdir = "attachments",
                    filename = item.storageName,
                    aad = item.id,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(4.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.viewer_close), tint = Color.White)
            }
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.media_delete), tint = Color.White)
            }
        }
    }
}
