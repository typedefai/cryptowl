package com.typedefai.cryptowl.vault

import android.content.Context
import com.typedefai.cryptowl.crypto.AesGcm
import com.typedefai.cryptowl.crypto.CryptoLog
import com.typedefai.cryptowl.crypto.KdfParams
import com.typedefai.cryptowl.crypto.KdfService
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.crypto.RandomUtil
import com.typedefai.cryptowl.crypto.WrappedKey
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher

/**
 * Top-Secret tier setup/unlock (docs/design.md L3) — v3 format.
 *
 * Android keeps its native two-factor gate:
 *   1. fingerprint → BioKey decrypts `ts_kek:biokey` → TS-KEK_bio
 *   2. the user types the secondary password → Argon2id → TS-KEK_pw
 *   3. both must match, then `top_secret_kek:ts_kek` is unwrapped.
 *
 * Format compatibility with the desktop client:
 * - `secondarySalt` is added to `vault.meta.salts` at setup time and the meta
 *   MAC is re-signed (setup re-enters the master password to derive SMK).
 * - The TopSecretKEK row is `top_secret_kek:ts_kek`, AAD = row id.
 * - T item payloads/DEKs are handled generically by [ItemRepository].
 */
class TopSecretService(
    private val context: Context,
    private val db: SqlCipherDb,
    private val kdf: KdfService = KdfService(),
    private val vaultId: String = VaultStore.DEFAULT_VAULT_ID,
) {

    fun isConfigured(): Boolean {
        val meta = loadMeta() ?: return false
        if (meta.salts.secondary == null) return false
        return rowExists(TS_KEK_ID) && rowExists(TOP_SECRET_KEK_ID)
    }

    // ------------------------------------------------------------------ setup

    /** ENCRYPT cipher (AAD `ts_kek:biokey`) to authorize with a fingerprint. */
    fun prepareSetupCipher(): Cipher =
        BioKeyManager.createEncryptCipher().apply { updateAAD(TS_KEK_AAD) }

    /**
     * Adds the secondary salt to `vault.meta` (re-signing its MAC), stores the
     * BioKey-wrapped TS-KEK and the TS-KEK-wrapped TopSecretKEK.
     *
     * [masterPassword] is required because the meta MAC key is SMK[32:64],
     * which only the password chain can derive (desktop does the same).
     */
    fun completeSetup(
        encryptCipher: Cipher,
        secondaryPassword: ProtectedValue,
        masterPassword: ProtectedValue,
    ) {
        val metaFile = VaultStore.metaFile(context, vaultId)
        val meta = loadMeta() ?: throw VaultOpenException("not a vault: $vaultId")
        if (meta.version != 3) throw VaultOpenException("Top Secret requires vault format v3")

        val secondarySalt = RandomUtil.generateSecureBytes(KEY_SIZE)
        val params = paramsOf(meta)
        val tsKek = kdf.createSecondaryKey(secondaryPassword, secondarySalt, params)
        val deviceSecret = DeviceSecretStore.getOrCreate(context)
        val tmk = kdf.createTransformedMasterKey(masterPassword, deviceSecret, meta.salts.argon2, params)
        val smk = kdf.createStretchedMasterKey(tmk, vaultId.toByteArray(Charsets.UTF_8), meta.salts.hkdf)

        try {
            val wrappedTsKek = tsKek.use { encryptCipher.doFinal(it) }
            val topSecretKek = ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(KEY_SIZE))
            try {
                val wrapped = kdf.wrapKey(topSecretKek, tsKek, aad = TOP_SECRET_KEK_AAD)
                val updated = meta.copy(
                    updatedAt = System.currentTimeMillis(),
                    salts = meta.salts.copy(secondary = secondarySalt.copyOf()),
                )
                val macKey = kdf.macKey(smk)
                try {
                    val signed = updated.copy(
                        mac = VaultMeta.Mac(
                            algorithm = "HMAC-SHA256",
                            value = VaultMetaJson.computeMac(updated, macKey.binaryValue()),
                        ),
                    )
                    db.beginTransaction()
                    try {
                        insertWrappedKey(TS_KEK_ID, TS_KEK_ROLE, TS_KEK_WRAPPER, encryptCipher.iv, wrappedTsKek)
                        insertWrappedKey(
                            TOP_SECRET_KEK_ID,
                            TOP_SECRET_KEK_ROLE,
                            TOP_SECRET_KEK_WRAPPER,
                            wrapped.nonce,
                            wrapped.cipherText + wrapped.authTag,
                        )
                        writeMetaAtomically(metaFile, signed)
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                    CryptoLog.d(C, "completeSetup: Top Secret configured (secondary salt added, meta re-signed)")
                } finally {
                    macKey.clear()
                }
            } finally {
                topSecretKek.clear()
            }
        } finally {
            tsKek.clear()
            tmk.clear()
            smk.clear()
            deviceSecret.clear()
            secondarySalt.fill(0)
        }
    }

    // ------------------------------------------------------------- per access

    /** DECRYPT cipher for `ts_kek:biokey` (factor 1). */
    fun prepareUnlockCipher(): Cipher {
        val row = loadRow(TS_KEK_ID) ?: error("no $TS_KEK_ID row in t_wrapped_key")
        CryptoLog.d(C, "prepareUnlockCipher: $TS_KEK_ID nonce=${row.nonce.toHexString(8)} (factor 1: fingerprint)")
        return BioKeyManager.createDecryptCipher(row.nonce).apply { updateAAD(TS_KEK_AAD) }
    }

    /** Fingerprint factor: unwraps the biometric TS-KEK copy. */
    fun unlockTsKek(cipher: Cipher): ProtectedValue {
        val row = loadRow(TS_KEK_ID) ?: error("no $TS_KEK_ID row in t_wrapped_key")
        val plain = try {
            cipher.doFinal(row.ciphertext + row.authTag)
        } catch (e: Throwable) {
            CryptoLog.e(C, "unlockTsKek: fingerprint factor failed", e)
            throw VaultOpenException("fingerprint key cannot unwrap the Top-Secret KEK")
        }
        return try {
            ProtectedValue.fromBinary(plain)
        } finally {
            plain.fill(0)
        }
    }

    /**
     * Password factor + unwrap: re-derives the TS-KEK from the typed secondary
     * password and requires it to equal the biometric copy before unwrapping
     * the TopSecretKEK.
     */
    fun unlockTopSecretKek(
        tsKekBio: ProtectedValue,
        secondaryPassword: ProtectedValue,
    ): ProtectedValue {
        val meta = loadMeta() ?: throw VaultOpenException("not a vault: $vaultId")
        val salt = meta.salts.secondary ?: error("vault.meta has no secondary salt")
        val tsKekPw = kdf.createSecondaryKey(secondaryPassword, salt, paramsOf(meta))
        try {
            val bio = tsKekBio.binaryValue()
            val pw = tsKekPw.binaryValue()
            try {
                if (!MessageDigest.isEqual(bio, pw)) {
                    CryptoLog.w(C, "unlockTopSecretKek: secondary password mismatch")
                    throw VaultOpenException("secondary password is incorrect")
                }
            } finally {
                bio.fill(0)
                pw.fill(0)
            }
        } finally {
            tsKekPw.clear()
        }
        val row = loadRow(TOP_SECRET_KEK_ID) ?: error("no $TOP_SECRET_KEK_ID row in t_wrapped_key")
        return kdf.unwrapKey(
            wrapped = WrappedKey(row.ciphertext, row.nonce, row.authTag),
            wrappingKey = tsKekBio,
            aad = TOP_SECRET_KEK_AAD,
        )
    }

    // ---------------------------------------------------------------- helpers

    private data class Row(val ciphertext: ByteArray, val nonce: ByteArray, val authTag: ByteArray)

    private fun loadMeta(): VaultMeta? {
        val metaFile = VaultStore.metaFile(context, vaultId)
        if (!metaFile.exists()) return null
        return try {
            VaultMetaJson.decode(metaFile.readText())
        } catch (e: Exception) {
            throw VaultOpenException("corrupt vault.meta: ${e.message}")
        }
    }

    private fun writeMetaAtomically(metaFile: File, meta: VaultMeta) {
        val tmp = File(metaFile.parentFile, "vault.meta.tmp")
        tmp.writeText(VaultMetaJson.encode(meta))
        if (!tmp.renameTo(metaFile)) {
            tmp.delete()
            throw VaultOpenException("failed to re-write vault.meta")
        }
    }

    private fun paramsOf(meta: VaultMeta): KdfParams = KdfParams(
        algorithm = meta.kdf.algorithm,
        mCostKiB = meta.kdf.mKib,
        tCost = meta.kdf.t,
        parallelism = meta.kdf.p,
    )

    private fun rowExists(id: String): Boolean =
        db.rawQuery("SELECT 1 FROM t_wrapped_key WHERE id = ? AND deleted_at IS NULL LIMIT 1", arrayOf<Any?>(id))
            .use { it.moveToFirst() }

    private fun loadRow(id: String): Row? =
        db.rawQuery(
            "SELECT ciphertext, nonce, auth_tag FROM t_wrapped_key WHERE id = ? AND deleted_at IS NULL LIMIT 1",
            arrayOf<Any?>(id),
        ).use { c ->
            if (c.moveToFirst()) Row(c.getBlob(0), c.getBlob(1), c.getBlob(2)) else null
        }

    private fun insertWrappedKey(
        id: String,
        role: String,
        wrapper: String,
        nonce: ByteArray,
        ciphertextWithTag: ByteArray,
    ) {
        val now = System.currentTimeMillis()
        val tagSize = AesGcm.TAG_SIZE_BYTES
        db.execSQL(
            "INSERT OR REPLACE INTO t_wrapped_key " +
                "(id, role, wrapper, algorithm, ciphertext, nonce, auth_tag, created_at, updated_at) " +
                "VALUES (?, ?, ?, 'AES-256-GCM', ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                id,
                role,
                wrapper,
                ciphertextWithTag.copyOf(ciphertextWithTag.size - tagSize),
                nonce,
                ciphertextWithTag.copyOfRange(ciphertextWithTag.size - tagSize, ciphertextWithTag.size),
                now,
                now,
            ),
        )
    }

    private companion object {
        const val C = "TopSecretService"
        const val KEY_SIZE = 32
        const val TS_KEK_ID = "ts_kek:biokey"
        const val TS_KEK_ROLE = "ts_kek"
        const val TS_KEK_WRAPPER = "biokey"
        const val TOP_SECRET_KEK_ID = "top_secret_kek:ts_kek"
        const val TOP_SECRET_KEK_ROLE = "top_secret_kek"
        const val TOP_SECRET_KEK_WRAPPER = "ts_kek"
        val TS_KEK_AAD = TS_KEK_ID.toByteArray(Charsets.UTF_8)
        val TOP_SECRET_KEK_AAD = TOP_SECRET_KEK_ID.toByteArray(Charsets.UTF_8)
    }
}
