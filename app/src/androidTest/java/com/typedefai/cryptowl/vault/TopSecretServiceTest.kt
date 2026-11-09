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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Top-Secret content crypto with a directly-provided TopSecretKEK (the
 * production key comes from the fingerprint + secondary-password gate, which
 * cannot run in a test). Covers DEK wrap, content AAD, encrypted t_note rows
 * and soft delete.
 */
@RunWith(AndroidJUnit4::class)
class TopSecretServiceTest {

    private lateinit var context: Context
    private lateinit var dbFile: File
    private lateinit var db: SQLiteDatabase
    private lateinit var topSecretKek: ProtectedValue

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        System.loadLibrary("sqlcipher")
        dbFile = File(context.cacheDir, "top-secret-service-test.db")
        dbFile.delete()
        val vaultKey = ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(32))
        db = vaultKey.use {
            SQLiteDatabase.openOrCreateDatabase(dbFile, vaultKey.asSqlCipherRawKey(), null, null)
        }
        SchemaApplier.migrate(db, context)
        topSecretKek = ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(32))
    }

    @After
    fun tearDown() {
        topSecretKek.clear()
        db.close()
        dbFile.delete()
    }

    @Test
    fun createReadUpdateDelete() {
        val service = TopSecretService(context, db)
        val id = service.create("Door codes", "1234-5678", topSecretKek)

        assertEquals(listOf("Door codes"), service.list().map { it.title })

        // metadata is plaintext-free: content column NULL, encrypted_data_id set
        db.rawQuery(
            "SELECT count(*) FROM t_note WHERE id = ? AND content IS NULL AND encrypted_data_id IS NOT NULL AND classification = 'T'",
            arrayOf(id),
        ).use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }

        val note = service.read(id, topSecretKek)
        assertEquals("Door codes", note?.title)
        assertEquals("1234-5678", note?.content)

        service.update(id, "Door codes 2", "9999", topSecretKek)
        assertEquals("Door codes 2", service.read(id, topSecretKek)?.title)
        assertEquals("9999", service.read(id, topSecretKek)?.content)

        service.delete(id)
        assertTrue(service.list().isEmpty())
        assertNull(service.read(id, topSecretKek))
    }

    @Test
    fun wrongKekCannotDecrypt() {
        val service = TopSecretService(context, db)
        val id = service.create("Bank", "top-secret", topSecretKek)
        val wrong = ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(32))
        try {
            service.read(id, wrong)
            fail("expected the DEK unwrap to fail with a wrong TopSecretKEK")
        } catch (expected: Exception) {
            // GCM tag mismatch
        } finally {
            wrong.clear()
        }
    }
}
