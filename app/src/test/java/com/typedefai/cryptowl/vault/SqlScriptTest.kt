package com.typedefai.cryptowl.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SqlScriptTest {

    @Test
    fun `splits a plain script on statement boundaries`() {
        val statements = SqlScript.splitStatements(
            "PRAGMA foreign_keys = ON;\nCREATE TABLE a (id TEXT);\nCREATE INDEX i ON a (id);",
        ).map { it.trim() }
        assertEquals(3, statements.size)
        assertEquals("PRAGMA foreign_keys = ON", statements[0])
        assertTrue(statements[1].startsWith("CREATE TABLE"))
        assertTrue(statements[2].startsWith("CREATE INDEX"))
    }

    @Test
    fun `semicolons inside string literals do not split`() {
        val statements = SqlScript.splitStatements(
            "INSERT INTO t VALUES ('a;b');SELECT 1;",
        ).map { it.trim() }
        assertEquals(2, statements.size)
        assertEquals("INSERT INTO t VALUES ('a;b')", statements[0])
    }

    @Test
    fun `trigger bodies stay in one statement`() {
        val script = """
            CREATE TRIGGER trg AFTER INSERT ON t BEGIN
              UPDATE t SET n = n + 1 WHERE id = NEW.id;
              INSERT INTO log VALUES (NEW.id);
            END;
            SELECT 1;
        """.trimIndent()
        val statements = SqlScript.splitStatements(script).map { it.trim() }
        assertEquals(2, statements.size)
        assertTrue(statements[0].contains("INSERT INTO log"))
        assertEquals("SELECT 1", statements[1])
    }
}
