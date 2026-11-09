package com.typedefai.cryptowl.vault

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.crypto.RandomUtil
import com.typedefai.cryptowl.model.Classification
import com.typedefai.cryptowl.model.ItemUpdate
import com.typedefai.cryptowl.model.NewItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `ItemRepository` parity with the desktop reference: versioning, fingerprint
 * suppression, C/S/T payload routing, tier changes and restore-as-new-version.
 */
@RunWith(AndroidJUnit4::class)
class ItemRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var vaultId: String
    private lateinit var session: VaultSession
    private lateinit var repo: ItemRepository
    private lateinit var kek: ProtectedValue
    private lateinit var topSecretKek: ProtectedValue

    @Before
    fun setUp() {
        vaultId = TestVaults.create(context)
        session = TestVaults.unlock(context, vaultId)
        repo = ItemRepository(session.db, session.hfk)
        kek = ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(32))
        topSecretKek = ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(32))
    }

    @After
    fun tearDown() {
        kek.clear()
        topSecretKek.clear()
        session.close()
        TestVaults.cleanup(context, vaultId)
    }

    @Test
    fun confidential_item_crud_and_history() {
        val id = repo.create(NewItem("plain", Classification.C, "Note", payload = "hello"))
        val item = repo.get(id)
        assertEquals("hello", item.payload)
        assertEquals(Classification.C, item.classification)
        assertEquals(1, repo.versions(id).size)

        // Unchanged save: no version row.
        assertFalse(repo.update(id, ItemUpdate("Note", Classification.C, payload = "hello")))
        assertEquals(1, repo.versions(id).size)

        // Changed payload: seq 2.
        assertTrue(repo.update(id, ItemUpdate("Note v2", Classification.C, payload = "hello2")))
        assertEquals(2, repo.versions(id).size)
        assertEquals("Note v2", repo.get(id).title)

        // Restore writes a new version (linear history).
        repo.restoreVersion(id, 1)
        assertEquals(3, repo.versions(id).size)
        assertEquals("hello", repo.get(id).payload)
        assertEquals(3, repo.versions(id).first().seq)
    }

    @Test
    fun secret_item_requires_the_kek_and_reuses_its_dek() {
        val payload = """{"notes":"n","secret":"s","url":"https://example.com","username":"u"}"""
        val id = repo.create(
            NewItem("password", Classification.S, "Example", payload = payload),
            kek = kek,
        )
        assertEquals(payload, repo.get(id, kek = kek).payload)

        // Without the KEK the item is locked, and the payload is not in content.
        var locked = false
        try {
            repo.get(id)
        } catch (e: VaultLockedException) {
            locked = true
        }
        assertTrue(locked)
        val content = session.db.rawQuery(
            "SELECT content, encrypted_data_id FROM t_item WHERE id = ?",
            arrayOf<Any?>(id),
        ).use { c ->
            c.moveToFirst()
            (if (c.isNull(0)) null else c.getString(0)) to c.getString(1)
        }
        assertEquals(null, content.first)
        assertTrue(content.second.isNotEmpty())

        // S -> S update reuses the DEK (desktop parity).
        val dekBefore = session.db.rawQuery(
            "SELECT e.dek_id FROM t_item i JOIN t_encrypted_data e ON e.id = i.encrypted_data_id WHERE i.id = ?",
            arrayOf<Any?>(id),
        ).use { c -> c.moveToFirst(); c.getString(0) }
        assertTrue(
            repo.update(
                id,
                ItemUpdate("Example", Classification.S, payload = payload.replace("\"s\"", "\"s2\"")),
                kek = kek,
            ),
        )
        val dekAfter = session.db.rawQuery(
            "SELECT e.dek_id FROM t_item i JOIN t_encrypted_data e ON e.id = i.encrypted_data_id WHERE i.id = ?",
            arrayOf<Any?>(id),
        ).use { c -> c.moveToFirst(); c.getString(0) }
        assertEquals(dekBefore, dekAfter)
        assertEquals(2, repo.versions(id).size)
    }

    @Test
    fun tier_change_rewrites_payload_and_keeps_snapshots() {
        val id = repo.create(NewItem("plain", Classification.C, "Note", payload = "secret text"))
        assertTrue(
            repo.update(
                id,
                ItemUpdate("Note", Classification.S, payload = "secret text"),
                kek = kek,
            ),
        )
        assertEquals(Classification.S, repo.get(id, kek = kek).classification)
        assertEquals("secret text", repo.get(id, kek = kek).payload)
        // The old C snapshot is still readable without a key.
        assertEquals("secret text", repo.version(id, 1).payload)
        assertEquals(Classification.C, repo.version(id, 1).classification)
        assertEquals(Classification.S, repo.version(id, 2, kek = kek).classification)
    }

    @Test
    fun top_secret_item_needs_the_top_secret_key() {
        val id = repo.create(
            NewItem("plain", Classification.T, "Top", payload = "t-content"),
            topSecretKek = topSecretKek,
        )
        assertEquals("t-content", repo.get(id, topSecretKek = topSecretKek).payload)
        var locked = false
        try {
            repo.get(id)
        } catch (e: VaultLockedException) {
            locked = true
        }
        assertTrue(locked)
        assertEquals(Classification.T, repo.version(id, 1, topSecretKek = topSecretKek).classification)
    }

    @Test
    fun soft_delete_hides_the_item_but_keeps_rows() {
        val id = repo.create(NewItem("plain", Classification.C, "Note", payload = "x"))
        repo.softDelete(id)
        assertTrue(repo.list().none { it.id == id })
        val deletedAtIsNull = session.db.rawQuery(
            "SELECT deleted_at FROM t_item WHERE id = ?",
            arrayOf<Any?>(id),
        ).use { c -> c.moveToFirst(); c.isNull(0) }
        assertFalse("soft delete stamps deleted_at", deletedAtIsNull)
    }
}
