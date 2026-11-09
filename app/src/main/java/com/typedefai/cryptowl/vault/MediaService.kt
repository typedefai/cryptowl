package com.typedefai.cryptowl.vault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.typedefai.cryptowl.crypto.CryptoLog
import com.typedefai.cryptowl.model.Classification
import com.typedefai.cryptowl.model.ItemSummary
import com.typedefai.cryptowl.model.NewItem
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** A media item paired with its `t_file` row. */
data class MediaEntry(val item: ItemSummary, val file: FileRecord)

/**
 * Confidential-tier media (docs/items.md type registry: `photo`, `video`).
 *
 * Every photo/video is a `t_item` (C tier) plus one `t_file` row. Files use
 * the FEK: embedded whole-file envelopes under the threshold, CWO1 chunked
 * envelopes in `attachments/` above it. Thumbnails are embedded in
 * `t_file.thumbnail`. Plaintext media never touches disk.
 */
class MediaService(
    private val context: Context,
    private val session: VaultSession,
) {

    private val items = ItemRepository(session.db, session.hfk)
    private val files = ItemFileRepository(
        db = session.db,
        attachmentsDir = File(VaultStore.vaultDir(context, session.vaultId), "attachments"),
        fek = session.fek,
        embedMaxBytes = session.embedMaxBytes,
    )

    fun list(): List<MediaEntry> =
        items.list()
            .filter { it.type == "photo" || it.type == "video" }
            .mapNotNull { summary ->
                files.listForItem(summary.id).firstOrNull()?.let { MediaEntry(summary, it) }
            }

    /** Imports an in-memory image (photo picker / capture). */
    fun importImage(bytes: ByteArray, name: String, mimeType: String?): String {
        val itemId = items.create(
            NewItem(
                type = "photo",
                classification = Classification.C,
                title = name,
                meta = mimeType ?: "",
            ),
        )
        try {
            files.importBytes(
                itemId = itemId,
                name = name,
                mimeType = mimeType,
                bytes = bytes,
                thumbnailPlaintext = makeThumbnail(bytes),
            )
        } catch (t: Throwable) {
            CryptoLog.e(C, "importImage failed", t)
            items.softDelete(itemId)
            throw t
        }
        return itemId
    }

    /** Imports a video (or any large file) by streaming from a content URI. */
    fun importUri(uri: Uri, name: String, mimeType: String?, isVideo: Boolean): String {
        val itemId = items.create(
            NewItem(
                type = if (isVideo) "video" else "photo",
                classification = Classification.C,
                title = name,
                meta = mimeType ?: "",
            ),
        )
        val temp = File(context.cacheDir, "import-${System.nanoTime()}.tmp")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            } ?: error("cannot open the selected file")

            val thumbnail = if (isVideo) videoThumbnail(uri)?.let { bitmap ->
                encodeThumbnail(bitmap).also { bitmap.recycle() }
            } else {
                temp.readBytes().let(::makeThumbnail)
            }
            files.importFile(
                itemId = itemId,
                name = name,
                mimeType = mimeType,
                source = temp,
                thumbnailPlaintext = thumbnail,
            )
            return itemId
        } catch (t: Throwable) {
            CryptoLog.e(C, "importUri failed", t)
            items.softDelete(itemId)
            throw t
        } finally {
            temp.delete()
        }
    }

    fun thumbnail(entry: MediaEntry): ByteArray? = files.decryptThumbnail(entry.file)

    fun original(entry: MediaEntry): ByteArray = files.readAll(entry.file.id)

    /** Decrypts the original into [target] (video playback bridge). */
    fun decryptToFile(entry: MediaEntry, target: File) = files.decryptToFile(entry.file.id, target)

    fun delete(entry: MediaEntry) {
        files.softDelete(entry.file.id)
        if (entry.file.storage == FileStorage.EXTERNAL) files.externalFile(entry.file).delete()
        items.softDelete(entry.item.id)
    }

    // ------------------------------------------------------------- thumbnails

    private fun makeThumbnail(bytes: ByteArray): ByteArray? {
        val bitmap = decodeBounded(bytes, THUMB_MAX_DIM) ?: return null
        return try {
            encodeThumbnail(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun encodeThumbnail(bitmap: Bitmap): ByteArray {
        val scaled = downscale(bitmap, THUMB_MAX_DIM)
        try {
            return java.io.ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, out)
                out.toByteArray()
            }
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun decodeBounded(bytes: ByteArray, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim * 2) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        return downscale(applyExifRotation(decoded, bytes), maxDim)
    }

    private fun applyExifRotation(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = try {
            ExifInterface(bytes.inputStream()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun downscale(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    private fun videoThumbnail(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(0)
        } catch (t: Throwable) {
            CryptoLog.e(C, "video thumbnail extraction failed", t)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private companion object {
        const val C = "MediaService"
        const val THUMB_MAX_DIM = 512
        const val THUMB_QUALITY = 85
    }
}
