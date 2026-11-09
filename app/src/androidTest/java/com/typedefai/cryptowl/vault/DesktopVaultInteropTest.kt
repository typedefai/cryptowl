package com.typedefai.cryptowl.vault

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.crypto.WrappedKey
import java.io.File
import java.util.zip.ZipInputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cross-implementation compatibility: a **desktop-created** vault
 * (`cryptowl-desktop`, SQLCipher 5 + `vault.meta` v3) must unlock on Android,
 * including the one-time device-secret re-bind, and its C and S items must be
 * readable.
 *
 * The fixture (`app/src/androidTest/assets/desktop_vault.zip`) was created by
 * the desktop client with password "correct horse battery staple"; it holds a
 * Confidential note and a Secret password login (KEK dev binding `kek:smk`).
 */
@RunWith(AndroidJUnit4::class)
class DesktopVaultInteropTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val vaultId = "vault"
    private lateinit var vaultDir: File

    @Before
    fun setUp() {
        vaultDir = VaultStore.vaultDir(context, vaultId)
        vaultDir.deleteRecursively()
        vaultDir.mkdirs()
        extractFixture()
    }

    @After
    fun tearDown() {
        VaultStore.vaultDir(context, vaultId).deleteRecursively()
        val index = VaultStore.indexFile(context)
        if (index.exists() && index.readText().contains("\"$vaultId\"")) index.delete()
    }

    @Test
    fun desktop_vault_unlocks_reads_items_and_rebinds_once() {
        val password = ProtectedValue.fromString(PASSWORD)
        try {
            // First open: desktop device_secret file → Android Keystore re-bind.
            val session = UnlockService(context).unlock(password, vaultId)
            val (note, login) = try {
                val repo = ItemRepository(session.db, session.hfk)
                val items = repo.list()
                val note = items.first { it.title == "Desktop note" }
                val login = items.first { it.title == "Desktop login" }
                assertEquals(2, items.size)

                // C-tier payload is readable directly.
                assertEquals("hello from the desktop client", repo.get(note.id).payload)

                // S-tier payload needs the KEK. The desktop dev binding
                // (`kek:smk`) is still there; unwrap it with the SMK derived
                // from the re-bound Android secret to prove the rewrap.
                val passwordValue = ProtectedValue.fromString(PASSWORD)
                val smk = try {
                    SecretAccess(context.applicationContext as Application, session)
                        .deriveSmk(passwordValue)
                } finally {
                    passwordValue.clear()
                }
                try {
                    val row = session.db.rawQuery(
                        "SELECT ciphertext, nonce, auth_tag FROM t_wrapped_key " +
                            "WHERE id = 'kek:smk' AND deleted_at IS NULL",
                        null,
                    ).use { c ->
                        assertTrue(c.moveToFirst())
                        Triple(c.getBlob(0), c.getBlob(1), c.getBlob(2))
                    }
                    val kdf = com.typedefai.cryptowl.crypto.KdfService()
                    val wrapping = kdf.vaultKey(smk)
                    val kek = try {
                        kdf.unwrapKey(
                            wrapped = WrappedKey(row.first, row.second, row.third),
                            wrappingKey = wrapping,
                            aad = "kek:smk".toByteArray(Charsets.UTF_8),
                        )
                    } finally {
                        wrapping.clear()
                    }
                    try {
                        val loginRecord = repo.get(login.id, kek = kek)
                        assertTrue(loginRecord.payload.contains("s3cret-from-desktop"))
                    } finally {
                        kek.clear()
                    }
                } finally {
                    smk.clear()
                }

                // Without the per-access key the S item stays locked.
                var locked = false
                try {
                    repo.get(login.id)
                } catch (e: VaultLockedException) {
                    locked = true
                }
                assertTrue(locked)
                note to login
            } finally {
                session.close()
            }
            assertTrue(note.title.isNotEmpty() && login.title.isNotEmpty())
        } finally {
            password.clear()
        }

        // Re-bind must have deleted the plaintext desktop secret and re-signed
        // both MACs: a second unlock (Android secret only) must succeed.
        assertFalse(VaultStore.deviceSecretFile(context, vaultId).exists())
        val second = ProtectedValue.fromString(PASSWORD)
        try {
            val session = UnlockService(context).unlock(second, vaultId)
            session.close()
        } finally {
            second.clear()
        }
    }

    @Test
    fun secret_item_stays_locked_without_the_kek() {
        val password = ProtectedValue.fromString(PASSWORD)
        try {
            val session = UnlockService(context).unlock(password, vaultId)
            try {
                val repo = ItemRepository(session.db, session.hfk)
                val login = repo.list().first { it.title == "Desktop login" }
                var locked = false
                try {
                    repo.get(login.id)
                } catch (e: VaultLockedException) {
                    locked = true
                }
                assertTrue(locked)
            } finally {
                session.close()
            }
        } finally {
            password.clear()
        }
    }

    private fun extractFixture() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        ZipInputStream(assets.open("desktop_vault.zip")).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.removePrefix("./")
                val target = File(vaultDir, name)
                require(target.canonicalPath.startsWith(vaultDir.canonicalPath + File.separator)) {
                    "zip slip: ${entry.name}"
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private companion object {
        const val PASSWORD = "correct horse battery staple"
    }
}
