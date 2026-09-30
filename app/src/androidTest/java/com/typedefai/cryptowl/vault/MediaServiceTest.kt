package com.typedefai.cryptowl.vault

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.crypto.RandomUtil
import java.io.ByteArrayOutputStream
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Confidential-tier media vault: files are FEK-encrypted CWO1 blobs and the
 * plaintext never reaches the disk. Uses a directly-provided FEK (the
 * production FEK comes from the unlocked VaultSession).
 */
@RunWith(AndroidJUnit4::class)
class MediaServiceTest {

    private lateinit var context: Context
    private lateinit var db: SQLiteDatabase
    private lateinit var session: VaultSession
    private val vaultId = "media-test-vault"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        System.loadLibrary("sqlcipher")
        VaultStore.vaultDir(context, vaultId).deleteRecursively()

        val dbFile = File(context.cacheDir, "media-service-test.db")
        dbFile.delete()
        val vaultKey = ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(32))
        db = vaultKey.use {
            SQLiteDatabase.openOrCreateDatabase(dbFile, vaultKey.asSqlCipherRawKey(), null, null)
        }
        SchemaApplier.migrate(db, context)
        session = VaultSession(
            vaultId = vaultId,
            db = db,
            vaultKey = ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(32)),
            fek = ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(32)),
        )
    }

    @After
    fun tearDown() {
        session.close()
        VaultStore.vaultDir(context, vaultId).deleteRecursively()
    }

    @Test
    fun importImageWritesEncryptedFileAndRow() {
        val service = MediaService(context, session)
        val png = pngBytes(8, 8)

        val item = service.importImage(png, "snapshot.png", "image/png")

        // t_file row (C tier: no DEK)
        val row = MediaRepository(db).get(item.id)
        assertNotNull(row)
        assertEquals("C", db.rawQuery("SELECT classification FROM t_file WHERE id = ?", arrayOf(item.id)).use {
            it.moveToFirst(); it.getString(0)
        })
        assertEquals(0, db.rawQuery("SELECT count(*) FROM t_file WHERE id = ? AND dek_id IS NULL", arrayOf(item.id)).use {
            it.moveToFirst(); it.getInt(0)
        })

        // the file on disk is CWO1 and does not contain the PNG magic
        val attachment = File(VaultStore.vaultDir(context, vaultId), "attachments/${item.storageName}")
        assertTrue(attachment.exists())
        val raw = attachment.readBytes()
        assertTrue(raw.size > png.size)
        assertFalse(raw.toString(Charsets.ISO_8859_1).contains("\u0089PNG"))

        // the thumbnail is written next to it and decrypts with the same AAD
        val thumbnail = File(VaultStore.vaultDir(context, vaultId), "thumbnails/${item.id}_t.cwo")
        assertTrue(thumbnail.exists())

        // decrypting returns the original bytes
        assertArrayEquals(png, service.originalBytes(item))

        // delete removes the row and the files
        service.delete(item.id)
        assertNull(MediaRepository(db).get(item.id))
        assertFalse(attachment.exists())
        assertFalse(thumbnail.exists())
    }

    @Test
    fun corruptCiphertextFailsToDecrypt() {
        val service = MediaService(context, session)
        val item = service.importImage(pngBytes(4, 4), "x.png", "image/png")
        val attachment = File(VaultStore.vaultDir(context, vaultId), "attachments/${item.storageName}")
        val raw = attachment.readBytes()
        raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 0x01).toByte()
        attachment.writeBytes(raw)
        try {
            service.originalBytes(item)
            org.junit.Assert.fail("expected tampered payload to fail authentication")
        } catch (expected: Exception) {
            // GCM tag mismatch
        }
    }

    private fun pngBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.MAGENTA)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}
