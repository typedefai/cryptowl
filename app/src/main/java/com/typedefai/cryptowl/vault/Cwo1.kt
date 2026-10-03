package com.typedefai.cryptowl.vault

import com.typedefai.cryptowl.crypto.AesGcm
import com.typedefai.cryptowl.crypto.CryptoLog
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.crypto.RandomUtil

/**
 * CWO1 — the self-describing encrypted media format (docs/moments.md §4),
 * byte-exact with the desktop reference (`wechat_sns_export/migrate_moments.py`).
 *
 *   whole-file (images/thumbnails/covers):
 *     header 22 B = b"CWO1" | u16 version(1) | u32 chunk_size(0) | nonce(12)
 *     payload     = AES-256-GCM(FEK, data, AAD = media row id) || tag(16)
 *
 *   chunked (video/audio):
 *     header 22 B = b"CWO1" | u16 version(1) | u32 chunk_size(65536)
 *                   | u64 chunk_count | iv_prefix(4)
 *     record N    = AES-256-GCM(FEK, chunk N, nonce = u64(N) || iv_prefix) || tag(16)
 *     record N starts at 22 + N * (chunk_size + 16) — random-access streaming.
 */
object Cwo1 {

    const val MAGIC = "CWO1"
    const val VERSION = 1
    const val HEADER_LEN = 22
    const val CHUNK_SIZE = 65536
    const val TAG_SIZE = 16

    class Header(
        val version: Int,
        val chunkSize: Int,
        val chunkCount: Long,
        /** 12 B whole-file nonce, or 4 B chunked iv_prefix. */
        val nonceOrIvPrefix: ByteArray,
    ) {
        val isChunked: Boolean get() = chunkSize > 0
        val recordLength: Int get() = chunkSize + TAG_SIZE
    }

    fun parseHeader(data: ByteArray): Header {
        require(data.size >= HEADER_LEN) { "too short for CWO1 header" }
        require(data[0] == 'C'.code.toByte() && data[1] == 'W'.code.toByte() &&
            data[2] == 'O'.code.toByte() && data[3] == '1'.code.toByte()
        ) { "not a CWO1 file" }
        val version = ((data[4].toInt() and 0xff) shl 8) or (data[5].toInt() and 0xff)
        require(version == VERSION) { "unsupported CWO1 version: $version" }
        val chunkSize = ((data[6].toInt() and 0xff) shl 24) or ((data[7].toInt() and 0xff) shl 16) or
            ((data[8].toInt() and 0xff) shl 8) or (data[9].toInt() and 0xff)
        return if (chunkSize == 0) {
            CryptoLog.d(C, "parseHeader: whole-file version=$version nonce=${data.copyOfRange(10, 22).toHexString(8)} total=${data.size}B")
            Header(version, 0, 0, data.copyOfRange(10, 22))
        } else {
            val count = (0 until 8).fold(0L) { acc, i -> (acc shl 8) or (data[10 + i].toLong() and 0xff) }
            CryptoLog.d(C, "parseHeader: chunked version=$version chunkSize=$chunkSize chunks=$count ivPrefix=${data.copyOfRange(18, 22).toHexString(4)} total=${data.size}B")
            Header(version, chunkSize, count, data.copyOfRange(18, 22))
        }
    }

    // ------------------------------------------------------------------ write

    fun encryptWholeFile(
        fek: ProtectedValue,
        aad: ByteArray,
        plaintext: ByteArray,
        nonce: ByteArray = RandomUtil.generateSecureBytes(AesGcm.NONCE_SIZE),
    ): ByteArray = fek.use { key ->
        CryptoLog.d(C, "encryptWholeFile: aad='${aad.decodeToString()}' plain=${plaintext.size}B fek(${CryptoLog.key(key)}) nonce=${nonce.toHexString(8)}")
        val encrypted = AesGcm.encrypt(key, nonce, aad, plaintext)
        headerWhole(nonce) + encrypted.cipherText + encrypted.authTag
    }

    /** Streams [plaintext] into chunked CWO1 records (64 KiB per record). */
    fun encryptChunked(
        fek: ProtectedValue,
        aad: ByteArray,
        plaintext: ByteArray,
        ivPrefix: ByteArray = RandomUtil.generateSecureBytes(4),
    ): ByteArray = fek.use { key ->
        val count = (plaintext.size + CHUNK_SIZE - 1) / CHUNK_SIZE
        CryptoLog.d(C, "encryptChunked: aad='${aad.decodeToString()}' plain=${plaintext.size}B chunks=$count fek(${CryptoLog.key(key)})")
        val out = java.io.ByteArrayOutputStream(HEADER_LEN + plaintext.size + count * TAG_SIZE)
        out.write(headerChunked(count.toLong(), ivPrefix))
        for (i in 0 until count) {
            val chunk = plaintext.copyOfRange(i * CHUNK_SIZE, minOf((i + 1) * CHUNK_SIZE, plaintext.size))
            val encrypted = AesGcm.encrypt(key, chunkNonce(i.toLong(), ivPrefix), aad, chunk)
            out.write(encrypted.cipherText)
            out.write(encrypted.authTag)
        }
        out.toByteArray()
    }

    // ------------------------------------------------------------------ read

    /** Decrypts a whole-file CWO1 blob in full. */
    fun decryptWholeFile(fek: ProtectedValue, aad: ByteArray, data: ByteArray): ByteArray {
        CryptoLog.d(C, "decryptWholeFile: aad='${aad.decodeToString()}' cipher=${data.size}B")
        val header = parseHeader(data)
        require(!header.isChunked) { "not a whole-file CWO1 blob" }
        return fek.use { key ->
            AesGcm.decrypt(
                key = key,
                nonce = header.nonceOrIvPrefix,
                aad = aad,
                encrypted = com.typedefai.cryptowl.crypto.AuthEncryptedData(
                    cipherText = data.copyOfRange(HEADER_LEN, data.size - TAG_SIZE),
                    authTag = data.copyOfRange(data.size - TAG_SIZE, data.size),
                ),
            )
        }
    }

    /** Random-access decrypt of record [index] of a chunked CWO1 file. */
    fun decryptChunkAt(fek: ProtectedValue, aad: ByteArray, data: ByteArray, index: Long): ByteArray {
        val header = parseHeader(data)
        require(header.isChunked) { "not a chunked CWO1 blob" }
        require(index in 0 until header.chunkCount) { "chunk index out of range" }
        return fek.use { key ->
            val offset = HEADER_LEN + (index * header.recordLength).toInt()
            val totalCt = data.size - HEADER_LEN - (header.chunkCount * TAG_SIZE).toInt()
            val chunkLen = minOf(header.chunkSize, totalCt - (index * header.chunkSize).toInt())
            val cipherText = data.copyOfRange(offset, offset + chunkLen)
            val authTag = data.copyOfRange(offset + chunkLen, offset + chunkLen + TAG_SIZE)
            AesGcm.decrypt(
                key = key,
                nonce = chunkNonce(index, header.nonceOrIvPrefix),
                aad = aad,
                encrypted = com.typedefai.cryptowl.crypto.AuthEncryptedData(cipherText, authTag),
            )
        }
    }

    /** Decrypts every chunk and concatenates (whole-file streaming result). */
    fun decryptChunked(fek: ProtectedValue, aad: ByteArray, data: ByteArray): ByteArray {
        val header = parseHeader(data)
        require(header.isChunked) { "not a chunked CWO1 blob" }
        val out = java.io.ByteArrayOutputStream()
        for (i in 0 until header.chunkCount) {
            out.write(decryptChunkAt(fek, aad, data, i))
        }
        return out.toByteArray()
    }

    /**
     * Streams a chunked CWO1 file to [target] one record at a time, so a large
     * video never has to be held in memory whole (playback bridge).
     */
    fun decryptChunkedToFile(fek: ProtectedValue, aad: ByteArray, source: java.io.File, target: java.io.File) {
        CryptoLog.d(C, "decryptChunkedToFile: src=$source (${source.length()}B) dst=$target aad='${aad.decodeToString()}'")
        java.io.RandomAccessFile(source, "r").use { raf ->
            val headerBytes = ByteArray(HEADER_LEN)
            raf.readFully(headerBytes)
            val header = parseHeader(headerBytes)
            require(header.isChunked) { "not a chunked CWO1 file" }
            fek.use { key ->
                target.outputStream().use { out ->
                    var offset = HEADER_LEN.toLong()
                    for (i in 0 until header.chunkCount) {
                        val isLast = i == header.chunkCount - 1
                        val recordLen = if (isLast) (raf.length() - offset).toInt() else header.recordLength
                        val cipherTextLen = recordLen - TAG_SIZE
                        val record = ByteArray(recordLen)
                        raf.seek(offset)
                        raf.readFully(record)
                        val plain = AesGcm.decrypt(
                            key = key,
                            nonce = chunkNonce(i, header.nonceOrIvPrefix),
                            aad = aad,
                            encrypted = com.typedefai.cryptowl.crypto.AuthEncryptedData(
                                cipherText = record.copyOf(cipherTextLen),
                                authTag = record.copyOfRange(cipherTextLen, recordLen),
                            ),
                        )
                        out.write(plain)
                        offset += recordLen
                    }
                    CryptoLog.d(C, "decryptChunkedToFile: done records=${header.chunkCount} plaintext=${target.length()}B")
                }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Header bytes for a chunked file (streaming writers need the count first). */
    fun headerForChunked(chunkCount: Long, ivPrefix: ByteArray): ByteArray = headerChunked(chunkCount, ivPrefix)

    /** Per-chunk nonce for a chunked file (nonce = u64(index) || iv_prefix). */
    fun nonceForChunk(index: Long, ivPrefix: ByteArray): ByteArray = chunkNonce(index, ivPrefix)

    private fun headerWhole(nonce: ByteArray): ByteArray {
        require(nonce.size == AesGcm.NONCE_SIZE) { "nonce must be 12 bytes" }
        return byteArrayOf('C'.code.toByte(), 'W'.code.toByte(), 'O'.code.toByte(), '1'.code.toByte()) +
            u16(VERSION) + u32(0) + nonce
    }

    private fun headerChunked(chunkCount: Long, ivPrefix: ByteArray): ByteArray {
        require(chunkCount in 0..0xFFFFFFFFFFFF) { "too many chunks" }
        return byteArrayOf('C'.code.toByte(), 'W'.code.toByte(), 'O'.code.toByte(), '1'.code.toByte()) +
            u16(VERSION) + u32(CHUNK_SIZE) + u64(chunkCount) + ivPrefix
    }

    private fun chunkNonce(index: Long, ivPrefix: ByteArray): ByteArray = u64(index) + ivPrefix

    private const val C = "Cwo1"

    private fun u16(v: Int): ByteArray = byteArrayOf((v shr 8).toByte(), v.toByte())

    private fun u32(v: Int): ByteArray = byteArrayOf(
        (v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte(),
    )

    private fun u64(v: Long): ByteArray = (7 downTo 0).map { ((v shr (it * 8)) and 0xff).toByte() }.toByteArray()
}
