package com.typedefai.cryptowl.vault

import android.content.Context
import com.typedefai.cryptowl.crypto.CryptoLog
import com.typedefai.cryptowl.crypto.ProtectedValue
import java.io.File

/**
 * The SQLCipher vault database — format v3.
 *
 * The schema is **fixed at vault creation** ([docs/schema.sql]): one
 * idempotent script, no `PRAGMA user_version`, no migrations. Opening a
 * database only *verifies* the core table shape and refuses anything else
 * with a "recreate the vault" error, exactly like the desktop client.
 */
object VaultDatabase {

    /** The tables every v3 vault must have (desktop `hasSchema`). */
    val CORE_TABLES = listOf(
        "t_wrapped_key",
        "t_data_encrypt_key",
        "t_encrypted_data",
        "t_item",
        "t_file",
        "t_item_version",
    )

    private const val SCHEMA_ASSET = "schema.sql"

    /** Creates and initializes a new vault database; fails if the file exists. */
    fun create(context: Context, file: File, vaultKey: ProtectedValue): SqlCipherDb {
        if (file.exists()) throw IllegalStateException("vault database already exists")
        val db = SqlCipherDb.openOrCreate(file, vaultKey.asSqlCipherRawKey())
        try {
            applySchema(db, context)
            checkSchema(db)
            CryptoLog.d(C, "create: schema applied (${CORE_TABLES.size} core tables)")
            return db
        } catch (t: Throwable) {
            CryptoLog.e(C, "create: schema failed, removing ${file.name}", t)
            db.close()
            file.delete()
            throw t
        }
    }

    /** Opens an existing vault database and verifies its schema shape. */
    fun open(context: Context, file: File, vaultKey: ProtectedValue): SqlCipherDb {
        val db = SqlCipherDb.open(file, vaultKey.asSqlCipherRawKey())
        try {
            checkSchema(db)
            return db
        } catch (t: Throwable) {
            db.close()
            throw t
        }
    }

    /** Read-only key/schema probe (used by restore validation). */
    fun verify(file: File, vaultKey: ProtectedValue): Boolean = try {
        SqlCipherDb.openReadOnly(file, vaultKey.asSqlCipherRawKey()).use {
            checkSchema(it)
            true
        }
    } catch (t: Throwable) {
        CryptoLog.w(C, "verify: database probe failed: ${t.message}")
        false
    }

    fun applySchema(db: SqlCipherDb, context: Context) {
        val sql = context.assets.open(SCHEMA_ASSET).bufferedReader().use { it.readText() }
        for (statement in SqlScript.splitStatements(sql)) {
            val trimmed = statement.trim()
            if (trimmed.isNotEmpty()) db.execSQL(trimmed)
        }
    }

    fun checkSchema(db: SqlCipherDb) {
        val placeholders = CORE_TABLES.joinToString(",") { "'$it'" }
        val count = db.rawQuery(
            "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name IN ($placeholders)",
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        if (count != CORE_TABLES.size) {
            throw IllegalStateException(
                "vault database schema is incomplete ($count/${CORE_TABLES.size} core tables) — " +
                    "this vault predates format v3; recreate it with this app",
            )
        }
    }

    private const val C = "VaultDatabase"
}

/** Splits a SQL script into executable statements. */
object SqlScript {

    /**
     * Splits on top-level `;`, quote-aware and trigger-body aware. Scripts are
     * comment-free by convention (a `;` inside a comment would produce bogus
     * statements).
     */
    fun splitStatements(sql: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var inString = false
        var beginDepth = 0
        var i = 0
        while (i < sql.length) {
            val c = sql[i]
            if (c == '\'') {
                inString = !inString
                current.append(c)
                i++
                continue
            }
            if (!inString && c == ';') {
                if (beginDepth > 0) current.append(c) else {
                    out.add(current.toString())
                    current.setLength(0)
                }
                i++
                continue
            }
            if (!inString && isWordAt(sql, i, "BEGIN")) {
                beginDepth++
                current.append("BEGIN")
                i += "BEGIN".length
                continue
            }
            if (!inString && isWordAt(sql, i, "END")) {
                beginDepth--
                current.append("END")
                i += "END".length
                continue
            }
            current.append(c)
            i++
        }
        if (current.isNotBlank()) out.add(current.toString())
        return out
    }

    private fun isWordAt(sql: String, index: Int, word: String): Boolean {
        if (!sql.regionMatches(index, word, 0, word.length, ignoreCase = true)) return false
        val beforeOk = index == 0 || (!sql[index - 1].isLetterOrDigit() && sql[index - 1] != '_')
        val after = index + word.length
        val afterOk = after == sql.length || (!sql[after].isLetterOrDigit() && sql[after] != '_')
        return beforeOk && afterOk
    }
}
