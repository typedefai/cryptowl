package com.typedefai.cryptowl.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.typedefai.cryptowl.vault.Cwo1
import com.typedefai.cryptowl.vault.VaultSession
import com.typedefai.cryptowl.vault.VaultStore
import java.io.File

/**
 * Decrypted-image cache shared by the moments timeline and the media vault.
 * Keyed by vault/subdir/filename so a re-scroll never re-decrypts; [clear] must
 * be called when the vault locks (decrypted bitmaps must not outlive a session).
 */
object MediaLoader {

    private val cache = object : LruCache<String, Bitmap>(maxCacheKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun loadCwo(context: Context, session: VaultSession, subdir: String, filename: String, aad: String): ImageBitmap? {
        val cacheKey = "${session.vaultId}/$subdir/$filename"
        cache.get(cacheKey)?.let { return it.asImageBitmap() }
        val file = File(File(VaultStore.vaultDir(context, session.vaultId), subdir), filename)
        if (!file.exists()) return null
        return try {
            val bytes = Cwo1.decryptWholeFile(session.fek, aad.toByteArray(Charsets.UTF_8), file.readBytes())
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            bytes.fill(0)
            cache.put(cacheKey, bitmap)
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    fun clear() {
        cache.evictAll()
    }

    private fun maxCacheKb(): Int {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return maxKb / 8
    }
}
