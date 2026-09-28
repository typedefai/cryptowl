package com.typedefai.cryptowl.vault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.typedefai.cryptowl.crypto.ProtectedValue
import java.io.File
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens a vault that was created on the desktop by the reference tooling
 * (`wechat_sns_export/vaultlib` + `migrate_moments.py` — the fixture vault in
 * `assets/fixture_vault.tar`), verifying the full cross-platform chain:
 * meta mac, config.sig, VaultKey unwrap, SQLCipher open, moments rows and
 * FEK-decrypted CWO1 media. Also verifies the one-time "re-bind" of a
 * desktop vault to the Android Keystore Device Secret.
 */
@RunWith(AndroidJUnit4::class)
class DesktopVaultIntegrationTest {

    private lateinit var context: Context
    private val vaultId = "personal"
    private val password = ProtectedValue.fromString("fixture-password")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val vaultDir = VaultStore.vaultDir(context, vaultId)
        if (vaultDir.exists()) vaultDir.deleteRecursively()

        // the fixture is laid out as <vaultId>/… — extract into the vaults root
        val tar = InstrumentationRegistry.getInstrumentation().context.assets.open("fixture_vault.tar")
        val bytes = tar.readBytes()
        val tarBytes = if (bytes[0] == 0x1f.toByte()) {
            GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
        } else {
            bytes
        }
        extractTar(tarBytes, VaultStore.vaultsDir(context))
        assertTrue(VaultStore.metaFile(context, vaultId).exists())
        assertTrue(VaultStore.deviceSecretFile(context, vaultId).exists())
    }

    @Test
    fun unlockOpensDesktopVaultAndReadsMoments() {
        val session = UnlockService(context).unlock(password, vaultId)

        try {
            val repo = MomentsRepository(session.db)
            val timeline = repo.timeline()

            assertEquals(2, timeline.size)

            val text = timeline.first { it.sourceCreatedAt == 1700000000000L }
            assertEquals("text", text.type)
            assertEquals("hello fixture", text.content)
            assertEquals("friends", text.visibility)
            assertEquals(1, text.likeCount)
            assertEquals(1, text.commentCount)
            assertEquals("A", text.likes.single().authorName)
            assertEquals("nice", text.comments.single().content)

            val media = timeline.first { it.sourceCreatedAt == 1700001000000L }
            assertEquals("media", media.type)
            assertTrue(media.isPrivate)
            assertEquals("Shanghai", poi(media.location))
            val item = media.media.single()
            assertEquals("image", item.mediaType)
            assertEquals(640, item.width)
            assertNotNull(item.filename)

            // decrypt the FEK-encrypted media and compare with the plaintext source
            val cwoFile = File(VaultStore.vaultDir(context, vaultId), "attachments/${item.filename}")
            assertTrue(cwoFile.exists())
            val plain = Cwo1.decryptWholeFile(session.fek, item.id.toByteArray(), cwoFile.readBytes())
            assertEquals(4096, plain.size)
            assertArrayEquals(ByteArray(256) { it.toByte() }.let { src ->
                ByteArray(4096) { i -> src[i % 256] }
            }, plain)
        } finally {
            session.close()
        }
    }

    @Test
    fun timelinePagesCarryChildrenAndSingleMomentMatches() {
        UnlockService(context).unlock(password, vaultId).use { session ->
            val repo = MomentsRepository(session.db)

            val first = repo.timelinePage(1)
            assertEquals(1, first.posts.size)
            assertTrue(first.hasMore)
            val paged = mutableListOf(first.posts.single())
            var offset = 1
            while (true) {
                val page = repo.timelinePage(1, offset)
                paged += page.posts
                if (!page.hasMore) break
                offset += page.posts.size
            }
            assertEquals(2, paged.size)
            val byTime = paged.sortedBy { it.sourceCreatedAt }
            assertEquals("hello fixture", byTime[0].content)
            assertEquals(1, byTime[0].likes.size)
            assertEquals("nice", byTime[0].comments.single().content)
            assertEquals("image", byTime[1].media.single().mediaType)

            // single-moment read must match the paged read-model
            val single = repo.moment(paged[0].id)
            assertNotNull(single)
            assertEquals(paged[0].copy(), single)
            assertEquals(null, repo.moment("nonexistent"))
        }
    }

    @Test
    fun unlockRebindsDesktopVaultToAndroidKeystore() {        val session = UnlockService(context).unlock(password, vaultId)
        session.close()

        // the desktop secret file is gone — the vault is now device-bound
        assertFalse(VaultStore.deviceSecretFile(context, vaultId).exists())

        // and it opens again with the Android Keystore Device Secret
        val again = UnlockService(context).unlock(password, vaultId)
        again.close()

        // wrong password is still rejected
        try {
            UnlockService(context).unlock(ProtectedValue.fromString("wrong-password"), vaultId)
            throw AssertionError("expected unlock failure")
        } catch (expected: VaultOpenException) {
            // expected
        }
    }

    // ------------------------------------------------------------- helpers

    private fun poi(locationJson: String?): String {
        if (locationJson == null) return ""
        return org.json.JSONObject(locationJson).optString("poi_name")
    }

    /** Minimal tar extractor: ustar headers only (the fixture is tiny). */
    private fun extractTar(tarBytes: ByteArray, destDir: File) {
        var offset = 0
        while (offset + 512 <= tarBytes.size) {
            val header = tarBytes.copyOfRange(offset, offset + 512)
            val name = header.copyOfRange(0, 100).toString(Charsets.UTF_8).trimEnd('\u0000')
            if (name.isEmpty()) break
            val size = header.copyOfRange(124, 136).toString(Charsets.UTF_8).trimEnd('\u0000', ' ')
                .toLong(8)
            val type = header[156].toInt().toChar()
            offset += 512
            val target = File(destDir, name.trimEnd('/'))
            when {
                type == '5' -> target.mkdirs()
                type == '0' -> {
                    target.parentFile?.mkdirs()
                    target.writeBytes(tarBytes.copyOfRange(offset, offset + size.toInt()))
                }
            }
            offset += ((size + 511) / 512 * 512).toInt()
        }
    }
}
