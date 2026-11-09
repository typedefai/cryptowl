package com.typedefai.cryptowl.vault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.crypto.RandomUtil
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Secret-tier password crypto + CRUD against a real SQLCipher DB with the
 * v1..v3 migration chain. The KEK is generated directly here: the production
 * path wraps it with the Keystore BioKey (a fingerprint prompt cannot run in
 * a test), but everything downstream — DEK wrap, content AAD, SQL rows,
 * soft delete — is the exact code the app uses.
 */
@RunWith(AndroidJUnit4::class)
class PasswordServiceTest {

    private lateinit var context: Context
    private lateinit var dbFile: File
    private lateinit var db: SQLiteDatabase
    private lateinit var kek: ProtectedValue

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        System.loadLibrary("sqlcipher")
        dbFile = File(context.cacheDir, "password-service-test.db")
        dbFile.delete()
        val vaultKey = ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(32))
        db = vaultKey.use {
            SQLiteDatabase.openOrCreateDatabase(dbFile, vaultKey.asSqlCipherRawKey(), null, null)
        }
        SchemaApplier.migrate(db, context)
        kek = ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(32))
    }

    @After
    fun tearDown() {
        kek.clear()
        db.close()
        dbFile.delete()
    }

    @Test
    fun migrationChainReachesVersion3() {
        assertEquals(3, db.version)
        val tables = mutableListOf<String>()
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
            while (c.moveToNext()) tables.add(c.getString(0))
        }
        assertTrue(tables.contains("t_password"))
    }

    @Test
    fun createReadUpdateDelete() {
        val service = PasswordService(db)
        val draft = PasswordDraft(
            title = "GitHub",
            username = "octocat",
            password = "s3cret-pw",
            url = "https://github.com",
            notes = "recovery codes in the safe",
        )

        val id = service.create(kek, draft)

        // list is title-only and needs no fingerprint
        assertEquals(listOf("GitHub"), service.list().map { it.title })

        // detail round-trips the full payload
        val created = service.detail(kek, id)
        assertEquals("octocat", created?.username)
        assertEquals("s3cret-pw", created?.password)
        assertEquals("https://github.com", created?.url)
        assertEquals("recovery codes in the safe", created?.notes)
        assertEquals("GitHub", created?.title)

        // update re-encrypts in place with the same DEK
        service.update(kek, draft.copy(id = id, title = "GitHub 2", password = "n3w-pw"))
        val updated = service.detail(kek, id)
        assertEquals("GitHub 2", updated?.title)
        assertEquals("n3w-pw", updated?.password)
        assertEquals("octocat", updated?.username)
        assertEquals(listOf("GitHub 2"), service.list().map { it.title })

        // soft delete hides the entry everywhere
        service.softDelete(id)
        assertTrue(service.list().isEmpty())
        assertNull(service.detail(kek, id))
    }

    @Test
    fun ciphertextIsNotPlaintextAndWrongKekFails() {
        val service = PasswordService(db)
        val id = service.create(kek, PasswordDraft(title = "Bank", password = "correct-horse"))

        val stored = db.rawQuery(
            "SELECT content FROM t_encrypted_data WHERE id = (SELECT encrypted_data_id FROM t_password WHERE id = ?)",
            arrayOf(id),
        ).use { c ->
            c.moveToFirst()
            c.getBlob(0)
        }
        assertTrue(stored.none { it.toInt() == 'c'.code })
        val wrongKek = ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(32))
        try {
            try {
                service.detail(wrongKek, id)
                fail("expected the unwrap to fail with a wrong KEK")
            } catch (expected: Exception) {
                assertNotEquals("correct-horse", expected.message)
            }
        } finally {
            wrongKek.clear()
        }
    }
}
