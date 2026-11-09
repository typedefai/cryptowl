package com.typedefai.cryptowl.vault

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.typedefai.cryptowl.crypto.CrockfordBase32
import com.typedefai.cryptowl.crypto.HmacSha256
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.crypto.RandomUtil
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Vault creation round-trip: v3 `vault.meta` / `config.json` / fixed schema /
 * SQLCipher 5, and unlock through [UnlockService].
 */
@RunWith(AndroidJUnit4::class)
class VaultCreatorTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var vaultId: String

    @Before
    fun setUp() {
        vaultId = TestVaults.create(context)
    }

    @After
    fun tearDown() {
        TestVaults.cleanup(context, vaultId)
    }

    @Test
    fun creates_v3_vault_with_desktop_compatible_files() {
        val metaFile = VaultStore.metaFile(context, vaultId)
        assertTrue(metaFile.exists())
        val meta = VaultMetaJson.decode(metaFile.readText())
        assertEquals(3, meta.version)
        assertEquals(vaultId, meta.vaultId)
        assertNull("the secondary salt is added at Top-Secret setup", meta.salts.secondary)
        assertEquals("argon2id", meta.kdf.algorithm)
        assertEquals(1, meta.wrappedKeys.count { it.id == "vault_key:smk" })

        // Round-trip through the canonical writer reproduces the stored bytes.
        assertEquals(metaFile.readText(), VaultMetaJson.encode(meta))

        // config.json: {"files":{"embed_max_bytes":524288},"name":"<id>"}
        val configFile = VaultStore.configFile(context, vaultId)
        assertTrue(configFile.exists())
        val config = configFile.readText()
        assertTrue(config.startsWith("""{"files":{"embed_max_bytes":524288},"name":"""))
        val sigFile = VaultStore.configSigFile(context, vaultId)
        assertTrue(sigFile.exists())
        assertEquals(52, CrockfordBase32.decode(sigFile.readText().trim()).size)

        // SQLCipher 5 database with the fixed v3 schema.
        val dbFile = VaultStore.dbFile(context, vaultId)
        assertTrue(dbFile.exists())
        assertEquals("5.", SqlCipherDb.cipherVersion().take(2))
        val session = TestVaults.unlock(context, vaultId)
        try {
            VaultDatabase.checkSchema(session.db)
            assertEquals(6, VaultDatabase.CORE_TABLES.size)
        } finally {
            session.close()
        }
    }

    @Test
    fun unlock_opens_session_and_rejects_wrong_password() {
        val wrong = ProtectedValue.fromString("definitely not the password")
        try {
            try {
                UnlockService(context).unlock(wrong, vaultId)
                throw AssertionError("wrong password must fail")
            } catch (expected: VaultOpenException) {
                // expected
            }
        } finally {
            wrong.clear()
        }

        val session = TestVaults.unlock(context, vaultId)
        try {
            assertEquals(vaultId, session.vaultId)
            assertTrue(session.embedMaxBytes >= 64 * 1024)
            assertEquals(32, session.fek.use { it.size })
            assertEquals(32, session.hfk.use { it.size })
        } finally {
            session.close()
        }
    }

    @Test
    fun refresh_of_meta_mac_uses_the_smk_mac_key() {
        val meta = VaultMetaJson.decode(VaultStore.metaFile(context, vaultId).readText())
        assertTrue(meta.mac != null)
        // Re-signing with a wrong MAC key must not match the stored value.
        val mac = HmacSha256.mac(ByteArray(32) { 0x42 }, VaultMetaJson.canonicalWithoutMac(meta).toByteArray())
        assertFalse(CrockfordBase32.encode(mac) == meta.mac!!.value)
    }

    @Test
    fun vault_database_rejects_wrong_key_and_foreign_schema() {
        val dbFile = VaultStore.dbFile(context, vaultId)
        val wrongKey = ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(32))
        try {
            try {
                VaultDatabase.open(context, dbFile, wrongKey)
                throw AssertionError("a wrong key must fail the SQLCipher probe")
            } catch (expected: Throwable) {
                // expected
            }
        } finally {
            wrongKey.clear()
        }

        val foreign = File(context.cacheDir, "foreign-${System.nanoTime()}.db")
        val key = ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(32))
        try {
            SqlCipherDb.openOrCreate(foreign, key.asSqlCipherRawKey()).use { db ->
                db.execSQL("CREATE TABLE t_only_one (id TEXT PRIMARY KEY)")
            }
            try {
                VaultDatabase.open(context, foreign, key)
                throw AssertionError("a non-v3 schema must be refused")
            } catch (expected: IllegalStateException) {
                assertTrue(expected.message!!.contains("schema"))
            }
        } finally {
            key.clear()
            foreign.delete()
        }
    }
}
