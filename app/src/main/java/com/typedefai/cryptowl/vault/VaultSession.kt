package com.typedefai.cryptowl.vault

import com.typedefai.cryptowl.crypto.CryptoLog
import com.typedefai.cryptowl.crypto.ProtectedValue

/**
 * An unlocked vault: the open SQLCipher database plus the keys of the
 * session (VaultKey = SQLCipher key; FEK = C-tier file encryption key;
 * HFK = history fingerprint key).
 *
 * The Secret-tier KEK is **not** part of the session: every S/T access
 * unwraps it through a fresh BiometricPrompt (docs/design.md L2).
 *
 * Keys are wiped and the DB closed by [close].
 */
class VaultSession(
    val vaultId: String,
    val db: SqlCipherDb,
    val vaultKey: ProtectedValue,
    val fek: ProtectedValue,
    val hfk: ProtectedValue,
    /** `config.json.files.embed_max_bytes` (v3 file-storage threshold). */
    val embedMaxBytes: Long = VaultMetaJson.DEFAULT_EMBED_MAX_BYTES,
) : AutoCloseable {

    override fun close() {
        CryptoLog.d("VaultSession", "close: wiping session vaultId=$vaultId (fek + hfk + vaultKey zeroized, db closed)")
        fek.clear()
        hfk.clear()
        vaultKey.clear()
        db.close()
    }
}
