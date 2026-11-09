package com.typedefai.cryptowl.vault

import com.typedefai.cryptowl.crypto.CryptoLog
import java.io.File

/**
 * Minimal raw SQLCipher 5 binding (vendored `libsqlcipher` in this app).
 *
 * Raw SQLCipher only — the vault DB is not managed by Room and there is no
 * `sqlcipher-android` AAR. The API deliberately mirrors the subset of the
 * old driver the repositories use: `rawQuery`/`execSQL` with bound arguments,
 * transactions, and a small forward-only cursor.
 */
class SqlCipherDb private constructor(private var handle: Long) : AutoCloseable {

    /** Forward-only cursor over a prepared statement. */
    class Cursor internal constructor(private var stmt: Long) : AutoCloseable {

        private var atRow = false

        fun moveToFirst(): Boolean = advance()

        /**
         * Advances to the next row. Like `android.database.Cursor`, calling
         * this before [moveToFirst] positions on the first row, so the
         * `while (cursor.moveToNext())` idiom works on a fresh cursor.
         */
        fun moveToNext(): Boolean = advance()

        private fun advance(): Boolean {
            if (stmt == 0L) return false
            atRow = SqlCipherNative.nativeStep(stmt) == ROW
            return atRow
        }

        val columnCount: Int
            get() = if (stmt == 0L) 0 else SqlCipherNative.nativeColumnCount(stmt)

        fun isNull(index: Int): Boolean =
            SqlCipherNative.nativeColumnType(stmt, index) == NULL

        fun getString(index: Int): String =
            String(SqlCipherNative.nativeColumnText(stmt, index), Charsets.UTF_8)

        fun getBlob(index: Int): ByteArray = SqlCipherNative.nativeColumnBlob(stmt, index)

        fun getLong(index: Int): Long = SqlCipherNative.nativeColumnLong(stmt, index)

        fun getInt(index: Int): Int = getLong(index).toInt()

        override fun close() {
            val current = stmt
            stmt = 0L
            atRow = false
            if (current != 0L) SqlCipherNative.nativeFinalize(current)
        }
    }

    // ------------------------------------------------------------- statements

    fun execSQL(sql: String) {
        checkOpen()
        SqlCipherNative.nativeExec(handle, sql)
    }

    /** Executes a statement with bound arguments (`String`, `Long`, `ByteArray`, `null`). */
    fun execSQL(sql: String, bindArgs: Array<Any?>) {
        checkOpen()
        val stmt = SqlCipherNative.nativePrepare(handle, sql)
        try {
            bind(stmt, bindArgs)
            var rc = SqlCipherNative.nativeStep(stmt)
            while (rc == ROW) rc = SqlCipherNative.nativeStep(stmt)
        } finally {
            SqlCipherNative.nativeFinalize(stmt)
        }
    }

    fun rawQuery(sql: String, bindArgs: Array<Any?>? = null): Cursor {
        checkOpen()
        val stmt = SqlCipherNative.nativePrepare(handle, sql)
        try {
            if (bindArgs != null) bind(stmt, bindArgs)
        } catch (e: Throwable) {
            SqlCipherNative.nativeFinalize(stmt)
            throw e
        }
        return Cursor(stmt)
    }

    fun rawQuery(sql: String, selectionArgs: Array<String>): Cursor =
        rawQuery(sql, selectionArgs as Array<Any?>)

    // ------------------------------------------------------------- transactions

    private var inTransaction = false
    private var transactionSuccessful = false

    val isInTransaction: Boolean get() = inTransaction

    fun beginTransaction() {
        check(!inTransaction) { "nested transactions are not supported" }
        execSQL("BEGIN IMMEDIATE")
        inTransaction = true
        transactionSuccessful = false
    }

    fun setTransactionSuccessful() {
        check(inTransaction) { "no transaction in progress" }
        transactionSuccessful = true
    }

    fun endTransaction() {
        check(inTransaction) { "no transaction in progress" }
        try {
            execSQL(if (transactionSuccessful) "COMMIT" else "ROLLBACK")
        } finally {
            inTransaction = false
            transactionSuccessful = false
        }
    }

    fun <T> transaction(block: () -> T): T {
        beginTransaction()
        return try {
            val result = block()
            setTransactionSuccessful()
            result
        } finally {
            endTransaction()
        }
    }

    // ------------------------------------------------------------- metadata

    val version: Int
        get() = rawQuery("PRAGMA user_version").use { if (it.moveToFirst()) it.getInt(0) else 0 }

    val changes: Int
        get() = SqlCipherNative.nativeChanges(handle)

    val lastInsertRowId: Long
        get() = SqlCipherNative.nativeLastInsertRowId(handle)

    override fun close() {
        val current = handle
        handle = 0L
        if (current != 0L) SqlCipherNative.nativeClose(current)
    }

    private fun checkOpen() {
        check(handle != 0L) { "database is closed" }
    }

    private fun bind(stmt: Long, args: Array<Any?>) {
        args.forEachIndexed { index, value ->
            val position = index + 1
            when (value) {
                null -> SqlCipherNative.nativeBindNull(stmt, position)
                is String -> SqlCipherNative.nativeBindText(stmt, position, value)
                is ByteArray -> SqlCipherNative.nativeBindBlob(stmt, position, value)
                is Int -> SqlCipherNative.nativeBindLong(stmt, position, value.toLong())
                is Long -> SqlCipherNative.nativeBindLong(stmt, position, value)
                is Boolean -> SqlCipherNative.nativeBindLong(stmt, position, if (value) 1L else 0L)
                else -> error("unsupported bind type: ${value::class.java.name}")
            }
        }
    }

    companion object {
        private const val ROW = 100
        private const val DONE = 101
        private const val NULL = 0

        init {
            System.loadLibrary("cryptowl")
        }

        /** SQLCipher version, e.g. `5.0.0-beta.1 community`. */
        fun cipherVersion(): String = SqlCipherNative.nativeVersion()

        /** Opens (or creates) a vault database with a raw-key expression. */
        fun openOrCreate(file: File, rawKeyExpression: String): SqlCipherDb {
            require(rawKeyExpression.startsWith("x'") && rawKeyExpression.endsWith("'")) {
                "the vault key must be the raw-key expression x'<hex>'"
            }
            file.parentFile?.mkdirs()
            val handle = SqlCipherNative.nativeOpen(
                file.absolutePath,
                rawKeyExpression,
                create = true,
                readOnly = false,
            )
            CryptoLog.d(C, "openOrCreate: ${file.name} (${file.length()}B)")
            return SqlCipherDb(handle)
        }

        /** Opens an existing vault database read/write (fails if the key is wrong). */
        fun open(file: File, rawKeyExpression: String): SqlCipherDb {
            require(rawKeyExpression.startsWith("x'") && rawKeyExpression.endsWith("'")) {
                "the vault key must be the raw-key expression x'<hex>'"
            }
            return SqlCipherDb(
                SqlCipherNative.nativeOpen(file.absolutePath, rawKeyExpression, false, false),
            )
        }

        /** Opens an existing vault database read-only. */
        fun openReadOnly(file: File, rawKeyExpression: String): SqlCipherDb =
            SqlCipherDb(
                SqlCipherNative.nativeOpen(file.absolutePath, rawKeyExpression, false, true),
            )

        private const val C = "SqlCipherDb"
    }
}

/**
 * `System.loadLibrary` holder for the JNI symbols implemented in
 * `app/src/main/cpp/sqlcipher-jni.cpp`. Kept separate so loading happens
 * once, on first use, before any native call.
 */
internal object SqlCipherNative {
    external fun nativeVersion(): String
    external fun nativeOpen(path: String, rawKey: String, create: Boolean, readOnly: Boolean): Long
    external fun nativeClose(handle: Long)
    external fun nativeExec(handle: Long, sql: String)
    external fun nativePrepare(handle: Long, sql: String): Long
    external fun nativeBindText(stmt: Long, index: Int, value: String)
    external fun nativeBindBlob(stmt: Long, index: Int, value: ByteArray)
    external fun nativeBindLong(stmt: Long, index: Int, value: Long)
    external fun nativeBindNull(stmt: Long, index: Int)
    external fun nativeStep(stmt: Long): Int
    external fun nativeColumnCount(stmt: Long): Int
    external fun nativeColumnType(stmt: Long, index: Int): Int
    external fun nativeColumnText(stmt: Long, index: Int): ByteArray
    external fun nativeColumnBlob(stmt: Long, index: Int): ByteArray
    external fun nativeColumnLong(stmt: Long, index: Int): Long
    external fun nativeFinalize(stmt: Long)
    external fun nativeChanges(handle: Long): Int
    external fun nativeLastInsertRowId(handle: Long): Long
}
