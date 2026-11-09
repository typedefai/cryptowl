package com.typedefai.cryptowl.vault

import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaApplierTest {

    // ------------------------------------------------------------- splitting

    @Test
    fun `splits plain statements on semicolons`() {
        val sql = """
            CREATE TABLE t_a (id TEXT);
            CREATE INDEX i_a ON t_a (id);

            CREATE TABLE t_b (id TEXT);
        """.trimIndent()
        assertEquals(3, SchemaApplier.splitStatements(sql).size)
    }

    @Test
    fun `keeps trigger bodies intact`() {
        val sql = """
            CREATE TABLE t_like (moment_id TEXT);
            CREATE TRIGGER tr_ai AFTER INSERT ON t_like
            BEGIN
                UPDATE t_moment SET like_count = like_count + 1
                WHERE id = NEW.moment_id;
                UPDATE t_other SET x = 1;
            END;
            CREATE TABLE t_after (id TEXT);
        """.trimIndent()
        val statements = SchemaApplier.splitStatements(sql)
        assertEquals(3, statements.size)
        assertEquals(true, statements[1].trim().endsWith("END"))
        assertEquals(true, statements[1].trim().contains("UPDATE t_other SET x = 1;"))
    }

    @Test
    fun `respects string literals containing semicolons`() {
        val sql = "INSERT INTO t VALUES ('a;b');"
        assertEquals(1, SchemaApplier.splitStatements(sql).size)
    }

    // ------------------------------------------------------------- planning

    @Test
    fun `parses migration file names`() {
        assertEquals(1, SchemaApplier.parseVersion("v1__init.sql"))
        assertEquals(12, SchemaApplier.parseVersion("v12__add_tags.sql"))
        assertNull(SchemaApplier.parseVersion("schema.sql"))
        assertNull(SchemaApplier.parseVersion("v__init.sql"))
        assertNull(SchemaApplier.parseVersion("v1x__init.sql"))
    }

    @Test
    fun `plan keeps only unapplied scripts in order`() {
        val files = listOf("v2__moments.sql", "v10__add_tags.sql", "v1__init.sql", "notes.txt")
        assertEquals(
            listOf(2 to "v2__moments.sql", 10 to "v10__add_tags.sql"),
            SchemaApplier.plan(files, currentVersion = 1),
        )
        // nothing to do when up to date
        assertEquals(emptyList<Pair<Int, String>>(), SchemaApplier.plan(files, currentVersion = 10))
    }

    // ------------------------------------------------------------- real scripts

    @Test
    fun `v2 parses the real moments schema`() {
        val sql = resource("v2__moments.sql")
        val statements = SchemaApplier.splitStatements(sql)
        // tables + indexes + 4 whole triggers, none shredded
        assertEquals(20, statements.size)
        assertTrue(statements.none { it.trim() == "END" })
        assertTrue(statements.count { it.trim().startsWith("CREATE TRIGGER") } == 4)
    }

    @Test
    fun `v1 core schema has no user_version pragma`() {
        // version bumping is owned by SchemaApplier, not the scripts
        assertFalse(resource("v1__init.sql").contains("PRAGMA user_version"))
    }

    // ------------------------------------------------------------- replay validation

    /**
     * Flyway-style `validate`: replays the whole chain on a fresh database and
     * checks the resulting state (objects + user_version). Plain sqlite via
     * JDBC, same SQL the desktop tool replays.
     */
    @Test
    fun `replays the full migration chain on a fresh database`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            for (name in listOf("v1__init.sql", "v2__moments.sql")) {
                replay(conn, resource(name))
            }
            conn.createStatement().use { st ->
                val tables = mutableSetOf<String>()
                st.executeQuery("SELECT name FROM sqlite_master WHERE type='table'").use { rs ->
                    while (rs.next()) tables.add(rs.getString(1))
                }
                for (expected in listOf(
                    "t_wrapped_key", "t_data_encrypt_key", "t_encrypted_data", "t_file",
                    "t_moment", "t_moment_comment", "t_moment_like", "t_friend", "t_moment_share",
                )) {
                    assertTrue("missing table $expected", expected in tables)
                }
                val triggers = mutableListOf<String>()
                st.executeQuery("SELECT name FROM sqlite_master WHERE type='trigger'").use { rs ->
                    while (rs.next()) triggers.add(rs.getString(1))
                }
                assertEquals(4, triggers.size)
                st.executeQuery("PRAGMA user_version").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(0, rs.getInt(1)) // scripts don't set it; the applier does
                }
            }
        }
    }

    private fun replay(conn: java.sql.Connection, sql: String) {
        for (statement in SchemaApplier.splitStatements(sql)) {
            val trimmed = statement.trim()
            if (trimmed.isEmpty()) continue
            conn.createStatement().use { it.execute(trimmed) }
        }
    }

    private fun resource(name: String): String =
        javaClass.getResourceAsStream("/migrations/$name")!!.bufferedReader().readText()
}
