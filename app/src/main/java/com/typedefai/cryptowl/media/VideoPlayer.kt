package com.typedefai.cryptowl.media

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

/**
 * Plays a decrypted media file from an app-private cache path.
 *
 * v3 external media is CWO1 chunked; the viewer decrypts it to a temp file
 * with [com.typedefai.cryptowl.vault.ItemFileRepository.decryptToFile] and
 * deletes that file as soon as playback stops (the documented moments.md §8
 * bridge; a media3 DataSource over `Cwo1.decryptChunkAt` is the future
 * hardening step that removes plaintext-on-disk entirely).
 */
@Composable
fun VideoFilePlayer(file: File, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(file) { ExoPlayer.Builder(context).build() }

    DisposableEffect(file) {
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
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

/** Placeholder spinner shown while the viewer decrypts a file. */
@Composable
fun MediaLoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White)
    }
}
