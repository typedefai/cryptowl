package com.typedefai.cryptowl.vault

import android.content.Context
import com.typedefai.cryptowl.crypto.CryptoLog
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Applies versioned migration scripts to a vault database, Flyway-style:
 *
 *   assets/migrations/v1__init.sql      (core schema — frozen baseline)
 *   assets/migrations/v2__moments.sql   (moments feature tables)
 *   assets/migrations/v3__<desc>.sql    (future changes, append-only)
 *
 * Progress is tracked by `PRAGMA user_version` — a plain int any SQLite
 * client (including the desktop tool) can read, instead of a tool-specific
 * history table. A script runs only when its version is greater than the
 * DB's current version; forward-only, never a destructive downgrade
 * (a vault from a *newer* app version fails loudly instead).
 *
 * The same scripts must be replayed by the desktop tool (`wechat_sns_export`)
 * so both platforms converge on identical schema state. The scripts are
 * plain SQL, comment-free — see AGENTS.md.
 *
 * `IF NOT EXISTS` is forced on every CREATE at execution time: a
 * desktop-created vault may already contain some later-version tables while
 * reporting an older `user_version`, and re-running must not fail.
 */
object SchemaApplier {

    private const val C = "SchemaApplier"
    private const val MIGRATIONS_DIR = "migrations"

    /** Applies every migration script newer than the DB's `user_version`. */
    fun migrate(db: net.zetetic.database.sqlcipher.SQLiteDatabase, context: Context) {
        val current = readVersion(db)
        var applied = 0
        for ((version, name) in plan((context.assets.list(MIGRATIONS_DIR) ?: emptyArray()).toList(), current)) {
            val sql = context.assets.open("$MIGRATIONS_DIR/$name").bufferedReader().readText()
            applyScript(db, sql)
            setVersion(db, version)
            applied++
            CryptoLog.d(C, "migrate: applied $name")
        }
        if (applied == 0) {
            CryptoLog.d(C, "migrate: up to date (user_version=$current)")
        }
    }

    // ------------------------------------------------------- planning (pure)

    /** Orders available scripts and keeps only those newer than [currentVersion]. */
    internal fun plan(assetNames: List<String>, currentVersion: Int): List<Pair<Int, String>> =
        assetNames
            .mapNotNull { name -> parseVersion(name)?.let { it to name } }
            .filter { (version, _) -> version > currentVersion }
            .sortedBy { (version, _) -> version }

    /** `v3__rename_column.sql` → 3; anything else is not a migration script. */
    internal fun parseVersion(fileName: String): Int? =
        Regex("""^v(\d+)__.+\.sql$""").matchEntire(fileName)?.groupValues?.get(1)?.toInt()

    internal fun readVersion(db: net.zetetic.database.sqlcipher.SQLiteDatabase): Int =
        db.rawQuery("PRAGMA user_version", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private fun setVersion(db: net.zetetic.database.sqlcipher.SQLiteDatabase, version: Int) {
        db.rawQuery("PRAGMA user_version = $version", null).use { it.moveToFirst() }
    }

    // ------------------------------------------------------- execution

    private fun applyScript(db: net.zetetic.database.sqlcipher.SQLiteDatabase, sql: String) {
        val idempotent = sql
            .replace("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS ")
            .replace("CREATE INDEX ", "CREATE INDEX IF NOT EXISTS ")
            .replace("CREATE TRIGGER ", "CREATE TRIGGER IF NOT EXISTS ")
        for (statement in splitStatements(idempotent)) {
            val trimmed = statement.trim()
            if (trimmed.isEmpty()) continue
            try {
                if (trimmed.startsWith("PRAGMA", ignoreCase = true)) {
                    // Some PRAGMAs (e.g. `secure_delete = ON`) return a result
                    // row; execSQL rejects statements that return rows.
                    db.rawQuery(trimmed, null).use { it.moveToFirst() }
                } else {
                    db.execSQL(trimmed)
                }
            } catch (e: Exception) {
                CryptoLog.e(C, "apply failed at statement: ${trimmed.take(100)}", e)
                throw e
            }
        }
    }

    /**
     * Splits a script into top-level statements. A `;` inside a trigger's
     * `BEGIN...END` body does not terminate the statement (trigger bodies
     * contain full statements of their own). Quote-aware ('...').
     * Note: a `CASE ... END` inside a trigger body would decrement the depth
     * prematurely — none of our scripts use one.
     */
    internal fun splitStatements(sql: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inString = false
        var triggerDepth = 0
        var i = 0
        while (i < sql.length) {
            val c = sql[i]
            if (c == '\'') {
                inString = !inString
                cur.append(c)
                i++
                continue
            }
            if (!inString && c == ';') {
                if (triggerDepth > 0) cur.append(c) else {
                    out.add(cur.toString())
                    cur.setLength(0)
                }
                i++
                continue
            }
            if (!inString && isWordAt(sql, i, "BEGIN")) {
                triggerDepth++
                cur.append("BEGIN")
                i += "BEGIN".length
                continue
            }
            if (!inString && isWordAt(sql, i, "END")) {
                triggerDepth--
                cur.append("END")
                i += "END".length
                continue
            }
            cur.append(c)
            i++
        }
        if (cur.isNotBlank()) out.add(cur.toString())
        return out
    }

    private fun isWordAt(sql: String, index: Int, word: String): Boolean {
        if (!sql.regionMatches(index, word, 0, word.length, ignoreCase = true)) return false
        val beforeOk = index == 0 || !sql[index - 1].isLetterOrDigit() && sql[index - 1] != '_'
        val after = index + word.length
        val afterOk = after == sql.length || !sql[after].isLetterOrDigit() && sql[after] != '_'
        return beforeOk && afterOk
    }
}
