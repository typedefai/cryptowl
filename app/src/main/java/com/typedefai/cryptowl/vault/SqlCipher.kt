package com.typedefai.cryptowl.vault

import com.typedefai.cryptowl.crypto.ProtectedValue

/**
 * SQLCipher raw-key expression (`x'<hex>'`) for [this] — the form vaultlib
 * passes to `sqlite3_key_v2`. A bare 32-byte blob is treated by SQLCipher as
 * a passphrase (PBKDF2), NOT as the raw key, so the vault key must always be
 * passed through here.
 */
fun ProtectedValue.asSqlCipherRawKey(): String = use { bytes ->
    buildString(bytes.size * 2 + 3) {
        append("x'")
        for (b in bytes) {
            append("0123456789abcdef"[(b.toInt() shr 4) and 0xf])
            append("0123456789abcdef"[b.toInt() and 0xf])
        }
        append('\'')
    }
}
