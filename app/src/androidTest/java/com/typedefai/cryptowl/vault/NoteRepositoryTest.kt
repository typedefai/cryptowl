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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Confidential-tier note CRUD against the real migration chain (v1..v4). */
@RunWith(AndroidJUnit4::class)
class NoteRepositoryTest {

    private lateinit var context: Context
    private lateinit var dbFile: File
    private lateinit var db: SQLiteDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        System.loadLibrary("sqlcipher")
        dbFile = File(context.cacheDir, "note-repository-test.db")
        dbFile.delete()
        val vaultKey = ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(32))
        db = vaultKey.use {
            SQLiteDatabase.openOrCreateDatabase(dbFile, vaultKey.asSqlCipherRawKey(), null, null)
        }
        SchemaApplier.migrate(db, context)
    }

    @After
    fun tearDown() {
        db.close()
        dbFile.delete()
    }

    @Test
    fun migrationReachesVersion4() {
        assertEquals(4, db.version)
        val tables = mutableListOf<String>()
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
            while (c.moveToNext()) tables.add(c.getString(0))
        }
        assertTrue(tables.contains("t_note"))
    }

    @Test
    fun createListGetUpdatePinDelete() {
        val repo = NoteRepository(db)

        val first = repo.create(NoteDraft(title = "Ideas", content = "# Hello\nbody"))
        val second = repo.create(NoteDraft(title = "Later", content = "todo", pinned = true))

        // pinned notes sort first
        assertEquals(listOf(second, first), repo.list().map { it.id })

        val note = repo.get(first)
        assertEquals("Ideas", note?.title)
        assertEquals("# Hello\nbody", note?.content)
        assertEquals(false, note?.pinned)

        repo.update(first, NoteDraft(id = first, title = "Ideas 2", content = "updated"))
        assertEquals("Ideas 2", repo.get(first)?.title)
        assertEquals("updated", repo.get(first)?.content)

        repo.setPinned(first, true)
        assertEquals(true, repo.get(first)?.pinned)

        repo.softDelete(first)
        assertNull(repo.get(first))
        assertEquals(listOf(second), repo.list().map { it.id })
    }

    @Test
    fun contentSurvivesUnicodeAndEmptyTitles() {
        val repo = NoteRepository(db)
        val id = repo.create(NoteDraft(title = "", content = "密鸮 · 🦉"))
        assertEquals("", repo.get(id)?.title)
        assertEquals("密鸮 · 🦉", repo.get(id)?.content)
    }
}
