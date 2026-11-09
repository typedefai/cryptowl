package com.typedefai.cryptowl.vault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.typedefai.cryptowl.crypto.KdfService
import com.typedefai.cryptowl.crypto.ProtectedValue
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end vault creation: key derivation chain (real Argon2 JNI) →
 * vault.meta with verifiable MAC → SQLCipher database initialized from
 * assets/migrations (v1 core + v2 moments). Mirrors the onboarding flow.
 */
@RunWith(AndroidJUnit4::class)
class VaultCreatorTest {

    private lateinit var context: Context
    private val vaultId = "test-vault"
    private val password = ProtectedValue.fromString("test-password-123")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val vaultDir = VaultStore.vaultDir(context, vaultId)
        if (vaultDir.exists()) vaultDir.deleteRecursively()
        VaultStore.indexFile(context).delete()
        context.getSharedPreferences("cryptowl.vault", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun createWritesMetaDatabaseAndIndex() {
        val kdf = KdfService()

        val meta = VaultCreator(context, kdf).create(password, vaultId)

        // vault.meta: version, wrapped vault_key:smk, verifiable MAC
        assertEquals(2, meta.version)
        assertEquals(vaultId, meta.vaultId)
        assertEquals("vault_key:smk", meta.wrappedKeys.single().id)
        assertTrue(VaultStore.metaFile(context, vaultId).exists())

        val deviceSecret = DeviceSecretStore.getOrCreate(context)
        val tmk = kdf.createTransformedMasterKey(password, deviceSecret, meta.salts.argon2)
        val smk = kdf.createStretchedMasterKey(tmk, vaultId.toByteArray(), meta.salts.hkdf)
        assertEquals(meta.mac?.value, VaultMetaJson.computeMac(meta, kdf.macKey(smk).binaryValue()))
        assertEquals("HMAC-SHA256", meta.mac?.algorithm)

        // database opens with the unwrapped VaultKey and has the full schema
        val vaultKey = kdf.unwrapKey(
            wrapped = meta.wrappedKeys.first { it.id == "vault_key:smk" }.toWrappedKey(),
            wrappingKey = kdf.vaultKey(smk),
            aad = "vault_key:smk".toByteArray(),
        )
        System.loadLibrary("sqlcipher")
        vaultKey.use { _ ->
            val db = SQLiteDatabase.openOrCreateDatabase(
                VaultStore.dbFile(context, vaultId), vaultKey.asSqlCipherRawKey(), null, null,
            )
            try {
                val tables = mutableListOf<String>()
                db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
                    while (cursor.moveToNext()) tables.add(cursor.getString(0))
                }
                for (expected in listOf("t_wrapped_key", "t_data_encrypt_key", "t_encrypted_data", "t_file")) {
                    assertTrue("missing table $expected", tables.contains(expected))
                }
                assertEquals(2, db.version) // v1 core + v2 moments applied
            } finally {
                db.close()
            }
        }

        // onboarding markers (the created vault, not the default one)
        assertTrue(VaultStore.isOnboarded(context, vaultId))
        assertEquals(vaultId, VaultStore.primaryVaultId(context))
    }

    @Test(expected = IllegalStateException::class)
    fun creatingTheSameVaultTwiceThrows() {
        VaultCreator(context).create(password, vaultId)
        VaultCreator(context).create(password, vaultId)
    }

    @Test
    fun deviceSecretIsStableAcrossReads() {
        val first = DeviceSecretStore.getOrCreate(context).binaryValue()
        val second = DeviceSecretStore.getOrCreate(context).binaryValue()
        assertEquals(first.toList(), second.toList())
        assertEquals(32, first.size)
    }
}
