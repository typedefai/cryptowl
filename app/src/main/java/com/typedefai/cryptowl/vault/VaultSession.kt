package com.typedefai.cryptowl.vault

import com.typedefai.cryptowl.crypto.CryptoLog
import com.typedefai.cryptowl.crypto.ProtectedValue
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * An unlocked vault: the open SQLCipher database plus the keys of the
 * session (VaultKey = SQLCipher key; FEK = C-tier file encryption key).
 * Keys are wiped and the DB closed by [close].
 */
class VaultSession(
    val vaultId: String,
    val db: SQLiteDatabase,
    val vaultKey: ProtectedValue,
    val fek: ProtectedValue,
) : AutoCloseable {

    override fun close() {
        CryptoLog.d("VaultSession", "close: wiping session vaultId=$vaultId (fek + vaultKey zeroized, db closed)")
        fek.clear()
        vaultKey.clear()
        db.close()
    }
}
