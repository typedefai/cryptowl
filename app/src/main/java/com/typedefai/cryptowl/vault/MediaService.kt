package com.typedefai.cryptowl.vault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.typedefai.cryptowl.crypto.CryptoLog
import androidx.exifinterface.media.ExifInterface
import com.typedefai.cryptowl.crypto.AesGcm
import com.typedefai.cryptowl.crypto.RandomUtil
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Confidential-tier media vault: import photos/videos into the vault as
 * whole-file (images) / chunked (videos) CWO1 blobs encrypted with the
 * session FEK — the same key material as the database, per docs/design.md.
 * Everything is written straight to `attachments/` + `thumbnails/`; the
 * plaintext never touches disk.
 */
class MediaService(
    private val context: Context,
    private val session: VaultSession,
    private val repo: MediaRepository = MediaRepository(session.db),
) {

    fun list(limit: Int = PAGE_SIZE, offset: Int = 0): MediaPage = repo.page(limit, offset)

    fun item(id: String): MediaItem? = repo.get(id)

    /** Encrypts an in-memory image (capture/import), with a downscaled thumbnail. */
    fun importImage(bytes: ByteArray, originalName: String?, mimeType: String?): MediaItem {
        val id = RandomUtil.generateUUID()
        CryptoLog.d(C, "importImage: id=$id name='$originalName' mime=$mimeType plain=${bytes.size}B fek(${CryptoLog.key(session.fek)}) aad=$id")
        val aad = id.toByteArray(Charsets.UTF_8)
        val encrypted = Cwo1.encryptWholeFile(session.fek, aad, bytes)
        writeAttachment(id, encrypted)

        val bitmap = decodeBounded(bytes, THUMB_MAX_DIM)
        if (bitmap != null) {
            writeThumbnail(id, bitmap)
            bitmap.recycle()
        }
        val item = MediaItem(
            id = id,
            storageName = attachmentName(id),
            originalName = originalName,
            mimeType = mimeType,
            sizeBytes = bytes.size.toLong(),
            createdAt = System.currentTimeMillis(),
        )
        repo.insert(item)
        CryptoLog.d(C, "importImage: done id=$id attachment=${attachmentFile(item)} thumbnail=${thumbnailFile(id)}")
        return item
    }

    /** Streams a video (or any large file) into chunked CWO1 records. */
    fun importFile(uri: Uri, originalName: String?, mimeType: String?, sizeBytes: Long): MediaItem {
        val id = RandomUtil.generateUUID()
        val aad = id.toByteArray(Charsets.UTF_8)
        val attachments = File(VaultStore.vaultDir(context, session.vaultId), "attachments").apply { mkdirs() }
        val target = File(attachments, attachmentName(id))
        val ivPrefix = RandomUtil.generateSecureBytes(4)

        val totalBytes = countBytes(uri)
        CryptoLog.d(C, "importFile: id=$id name='$originalName' mime=$mimeType uri=$uri total=${totalBytes}B fek(${CryptoLog.key(session.fek)})")
        val chunkCount = if (totalBytes == 0L) 0L else (totalBytes + Cwo1.CHUNK_SIZE - 1) / Cwo1.CHUNK_SIZE
        session.fek.use { key ->
            FileOutputStream(target).use { out ->
                out.write(Cwo1.headerForChunked(chunkCount, ivPrefix))
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("cannot open the selected file")
                input.use {
                    val buffer = ByteArray(Cwo1.CHUNK_SIZE)
                    var index = 0L
                    while (true) {
                        var filled = 0
                        while (filled < buffer.size) {
                            val read = it.read(buffer, filled, buffer.size - filled)
                            if (read < 0) break
                            filled += read
                        }
                        if (filled == 0) break
                        val chunk = if (filled == buffer.size) buffer else buffer.copyOf(filled)
                        val encrypted = AesGcm.encrypt(
                            key = key,
                            nonce = Cwo1.nonceForChunk(index, ivPrefix),
                            aad = aad,
                            plainText = chunk,
                        )
                        out.write(encrypted.cipherText)
                        out.write(encrypted.authTag)
                        index++
                    }
                }
            }
        }

        // Video frame from the source (before it is ever stored in the clear).
        if (mimeType?.startsWith("video/") == true) {
            videoThumbnail(uri)?.let {
                writeThumbnail(id, it)
                it.recycle()
            }
        }

        val item = MediaItem(
            id = id,
            storageName = attachmentName(id),
            originalName = originalName,
            mimeType = mimeType,
            sizeBytes = if (sizeBytes > 0) sizeBytes else totalBytes,
            createdAt = System.currentTimeMillis(),
        )
        repo.insert(item)
        CryptoLog.d(C, "importFile: done id=$id chunks=${if (totalBytes == 0L) 0 else (totalBytes + Cwo1.CHUNK_SIZE - 1) / Cwo1.CHUNK_SIZE} " +
            "attachment=${attachmentFile(item)}")
        return item
    }

    /** Decrypted original bytes (images; videos are streamed by the player). */
    fun originalBytes(item: MediaItem): ByteArray {
        val file = attachmentFile(item)
        require(file.exists()) { "missing attachment: ${item.storageName}" }
        val aad = item.id.toByteArray(Charsets.UTF_8)
        val cipher = file.readBytes()
        CryptoLog.d(C, "originalBytes: id=${item.id} file=$file cipher=${cipher.size}B fek(${CryptoLog.key(session.fek)}) aad=${item.id}")
        return Cwo1.decryptWholeFile(session.fek, aad, cipher)
    }

    fun delete(id: String) {
        val item = repo.get(id)
        CryptoLog.d(C, "delete: id=$id exists=${item != null}")
        repo.softDelete(id)
        if (item != null) {
            attachmentFile(item).delete()
            thumbnailFile(id).delete()
        }
    }

    // ---------------------------------------------------------------- helpers

    fun attachmentFile(item: MediaItem): File =
        File(File(VaultStore.vaultDir(context, session.vaultId), "attachments"), item.storageName)

    fun thumbnailFile(id: String): File =
        File(File(VaultStore.vaultDir(context, session.vaultId), "thumbnails"), thumbnailName(id))

    private fun writeAttachment(id: String, bytes: ByteArray) {
        val dir = File(VaultStore.vaultDir(context, session.vaultId), "attachments").apply { mkdirs() }
        File(dir, attachmentName(id)).writeBytes(bytes)
    }

    private fun writeThumbnail(id: String, bitmap: Bitmap) {
        val dir = File(VaultStore.vaultDir(context, session.vaultId), "thumbnails").apply { mkdirs() }
        val scaled = downscale(bitmap, THUMB_MAX_DIM)
        val jpeg = java.io.ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, out)
            out.toByteArray()
        }
        if (scaled !== bitmap) scaled.recycle()
        val encrypted = Cwo1.encryptWholeFile(session.fek, id.toByteArray(Charsets.UTF_8), jpeg)
        jpeg.fill(0)
        File(dir, thumbnailName(id)).writeBytes(encrypted)
    }

    private fun countBytes(uri: Uri): Long {
        val input = context.contentResolver.openInputStream(uri) ?: return 0
        return input.use {
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = it.read(buffer)
                if (read < 0) break
                total += read
            }
            total
        }
    }

    private fun videoThumbnail(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(0)
        } catch (e: Throwable) {
            CryptoLog.e(C, "video thumbnail extraction failed", e)
            null
        } finally {
            runCatching { retriever.release() }
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
        val oriented = applyExifRotation(decoded, bytes)
        return downscale(oriented, maxDim)
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

    private fun attachmentName(id: String) = "$id.cwo"
    private fun thumbnailName(id: String) = "${id}_t.cwo"

    private companion object {
        const val C = "MediaService"
        const val PAGE_SIZE = 60
        const val THUMB_MAX_DIM = 512
        const val THUMB_QUALITY = 85
    }
}
