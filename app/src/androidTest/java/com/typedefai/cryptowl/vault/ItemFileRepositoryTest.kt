package com.typedefai.cryptowl.vault

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.typedefai.cryptowl.crypto.RandomUtil
import com.typedefai.cryptowl.model.Classification
import com.typedefai.cryptowl.model.NewItem
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `t_file` file storage: embedded whole-file envelopes under the threshold,
 * CWO1 chunked envelopes above it, thumbnail AAD and delete behavior.
 */
@RunWith(AndroidJUnit4::class)
class ItemFileRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var vaultId: String
    private lateinit var session: VaultSession
    private lateinit var repo: ItemRepository
    private lateinit var files: ItemFileRepository

    @Before
    fun setUp() {
        vaultId = TestVaults.create(context)
        session = TestVaults.unlock(context, vaultId)
        repo = ItemRepository(session.db, session.hfk)
        files = ItemFileRepository(
            db = session.db,
            attachmentsDir = TestVaults.attachmentsDir(context, vaultId),
            fek = session.fek,
            embedMaxBytes = session.embedMaxBytes,
        )
    }

    @After
    fun tearDown() {
        session.close()
        TestVaults.cleanup(context, vaultId)
    }

    @Test
    fun embedded_file_round_trips_with_thumbnail() {
        val itemId = repo.create(NewItem("photo", Classification.C, "small.bin"))
        val bytes = ByteArray(2048) { (it % 251).toByte() }
        val thumbnail = ByteArray(128) { 7 }
        val fileId = files.importBytes(itemId, "small.bin", "application/octet-stream", bytes, thumbnail)

        val record = files.get(fileId)!!
        assertEquals(FileStorage.EMBEDDED, record.storage)
        assertTrue(record.content!!.size > bytes.size)
        assertArrayEquals(bytes, files.readAll(fileId))
        assertArrayEquals(thumbnail, files.decryptThumbnail(record))
        assertEquals(1, files.listForItem(itemId).size)
    }

    @Test
    fun large_file_uses_cwo1_chunked_storage() {
        val itemId = repo.create(NewItem("video", Classification.C, "large.bin"))
        val bytes = RandomUtil.generateSecureBytes((session.embedMaxBytes + 1024).toInt())
        val fileId = files.importBytes(itemId, "large.bin", "application/octet-stream", bytes)

        val record = files.get(fileId)!!
        assertEquals(FileStorage.EXTERNAL, record.storage)
        val external = files.externalFile(record)
        assertTrue(external.exists())
        // 22-byte CWO1 header, 64 KiB records.
        val header = external.readBytes().copyOfRange(0, 4)
        assertEquals("CWO1", String(header))
        assertArrayEquals(bytes, files.readAll(fileId))

        // Streaming decrypt to a file must reproduce the plaintext.
        val target = File(context.cacheDir, "decrypted-${System.nanoTime()}.bin")
        try {
            files.decryptToFile(fileId, target)
            assertArrayEquals(bytes, target.readBytes())
        } finally {
            target.delete()
        }
    }

    @Test
    fun soft_delete_hides_files() {
        val itemId = repo.create(NewItem("photo", Classification.C, "a.bin"))
        val fileId = files.importBytes(itemId, "a.bin", null, ByteArray(16))
        files.softDelete(fileId)
        assertEquals(null, files.get(fileId))
        assertTrue(files.listForItem(itemId).isEmpty())
        assertNotNull(repo.get(itemId))
    }
}
