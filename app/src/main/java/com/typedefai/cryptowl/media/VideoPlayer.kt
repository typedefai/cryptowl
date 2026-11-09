package com.typedefai.cryptowl.media

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.typedefai.cryptowl.vault.Cwo1
import com.typedefai.cryptowl.vault.VaultSession
import com.typedefai.cryptowl.vault.VaultStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Plays a chunked CWO1 video/audio attachment.
 *
 * The encrypted records are streamed to an app-private cache file (the
 * documented moments.md §8 bridge: `decryptChunkAt` → local temp file →
 * player) and the temp file is deleted as soon as playback stops. A future
 * hardening step is a media3 `DataSource` fed directly by [Cwo1.decryptChunkAt]
 * with no plaintext on disk at all.
 */
@Composable
fun Cwo1VideoPlayer(
    session: VaultSession,
    subdir: String,
    filename: String,
    aad: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var tempFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(session, subdir, filename, aad) {
        tempFile = withContext(Dispatchers.IO) {
            try {
                val source = File(File(VaultStore.vaultDir(context, session.vaultId), subdir), filename)
                val target = File(context.cacheDir, "cwl_play_${UUID.randomUUID()}.bin")
                Cwo1.decryptChunkedToFile(session.fek, aad.toByteArray(Charsets.UTF_8), source, target)
                target
            } catch (e: Throwable) {
                Log.e(TAG, "video decrypt failed for $filename", e)
                null
            }
        }
    }

    val file = tempFile
    if (file == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    val player = remember(file) { ExoPlayer.Builder(context).build() }
    DisposableEffect(file) {
        player.setMediaItem(Media3Item.fromUri(Uri.fromFile(file)))
        player.prepare()
        player.playWhenReady = true
        onDispose {
            player.release()
            file.delete()
        }
    }
    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
        modifier = modifier,
    )
}

private const val TAG = "cwl:Cwo1VideoPlayer"
