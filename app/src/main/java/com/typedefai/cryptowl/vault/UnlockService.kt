package com.typedefai.cryptowl.vault

import android.content.Context
import com.typedefai.cryptowl.crypto.CryptoLog
import com.typedefai.cryptowl.crypto.CrockfordBase32
import com.typedefai.cryptowl.crypto.HmacSha256
import com.typedefai.cryptowl.crypto.KdfParams
import com.typedefai.cryptowl.crypto.KdfService
import com.typedefai.cryptowl.crypto.toHexString
import com.typedefai.cryptowl.crypto.ProtectedValue
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher

/** Thrown when the vault cannot be opened (wrong password, tampering...). */
class VaultOpenException(message: String) : Exception(message)

/**
 * Opens a vault with the master password — mirror of
 * `wechat_sns_export/vaultlib/vault.py::Vault.open`:
 *
 *   1. parse + version-check vault.meta
 *   2. Device Secret: Android Keystore, or the desktop `device_secret` file
 *      for a vault created/migrated on the desktop
 *   3. derive TMK → SMK, verify config.sig and vault.meta mac
 *   4. unwrap `vault_key:smk` → VaultKey → open the SQLCipher database
 *   5. re-bind desktop vaults: re-wrap VaultKey with the Android Device
 *      Secret and delete `device_secret` (design "re-bind on import")
 *
 * All intermediates are wiped before returning.
 */
class UnlockService(
    private val context: Context,
    private val kdf: KdfService = KdfService(),
) {

    fun unlock(
        masterPassword: ProtectedValue,
        vaultId: String = VaultStore.DEFAULT_VAULT_ID,
    ): VaultSession {
        val metaFile = VaultStore.metaFile(context, vaultId)
        if (!metaFile.exists()) throw VaultOpenException("not a vault: $vaultId")
        CryptoLog.d(C, "unlock: vaultId=$vaultId meta=${metaFile.length()}B db=${VaultStore.dbFile(context, vaultId).length()}B")
        val meta = try {
            VaultMetaJson.decode(metaFile.readText())
        } catch (e: Exception) {
            throw VaultOpenException("corrupt vault.meta: ${e.message}")
        }
        // v3 is a fresh format: the schema is fixed at creation and old
        // (v1/v2) vaults are refused, matching the desktop client. There is
        // no in-place migration path.
        if (meta.version != META_VERSION) {
            throw VaultOpenException(
                "unsupported vault format version ${meta.version} — " +
                    "recreate the vault with this app (format v$META_VERSION)",
            )
        }
        CryptoLog.d(C,
            "unlock: meta version=${meta.version} kdf=${meta.kdf.algorithm} " +
                "m=${meta.kdf.mKib}KiB t=${meta.kdf.t} p=${meta.kdf.p} " +
                "argon2Salt=${meta.salts.argon2.toHexString(8)} hkdfSalt=${meta.salts.hkdf.toHexString(8)} " +
                "wrappedKeys=${meta.wrappedKeys.map { it.id }}",
        )

        val params = KdfParams(
            algorithm = meta.kdf.algorithm,
            mCostKiB = meta.kdf.mKib,
            tCost = meta.kdf.t,
            parallelism = meta.kdf.p,
        )
        val desktopSecret = readDesktopSecret(vaultId)
        val deviceSecret = desktopSecret?.let { ProtectedValue.fromBinary(it) }
            ?: DeviceSecretStore.getOrCreate(context)
        CryptoLog.d(C,
            "unlock: deviceSecret from ${if (desktopSecret != null) "desktop device_secret file (re-bind needed)" else "Android Keystore"} " +
                "deviceSecret(${CryptoLog.key(deviceSecret)})",
        )

        val tmk = kdf.createTransformedMasterKey(masterPassword, deviceSecret, meta.salts.argon2, params)
        val smk = kdf.createStretchedMasterKey(tmk, vaultId.toByteArray(Charsets.UTF_8), meta.salts.hkdf)
        val macKey = kdf.macKey(smk)

        try {
            val embedMaxBytes = verifyConfig(meta, macKey)
            CryptoLog.d(C, "unlock: config.sig verified (embed_max_bytes=$embedMaxBytes)")
            verifyMetaMac(meta, macKey)
            CryptoLog.d(C, "unlock: vault.meta mac verified")

            val wrappedVaultKey = meta.wrappedKeys.firstOrNull { it.id == WRAPPED_VAULT_KEY_SMK }
                ?: throw VaultOpenException("no vault_key:smk wrapped key in vault.meta")
            val vaultKey = kdf.unwrapKey(
                wrapped = wrappedVaultKey.toWrappedKey(),
                wrappingKey = kdf.vaultKey(smk),
                aad = WRAPPED_VAULT_KEY_SMK.toByteArray(Charsets.UTF_8),
            )
            CryptoLog.d(C, "unlock: vault_key unwrapped vaultKey(${CryptoLog.key(vaultKey)})")

            val db = openDatabase(vaultId, vaultKey)
            CryptoLog.d(C, "unlock: db opened " +
                "db=${VaultStore.dbFile(context, vaultId)} (${VaultStore.dbFile(context, vaultId).length()}B)")
            val fek = kdf.fileKey(vaultKey)
            val hfk = kdf.fingerprintKey(vaultKey)
            CryptoLog.d(C, "unlock: session ready vaultId=$vaultId fek(${CryptoLog.key(fek)}) hfk(${CryptoLog.key(hfk)})")

            // Desktop-created vault: re-bind to this device before handing out.
            if (desktopSecret != null) {
                rebindVaultKey(meta, vaultId, db, smk, masterPassword, vaultKey)
                CryptoLog.d(C, "unlock: rebind to Android Keystore done, desktop device_secret deleted")
            }
            return VaultSession(vaultId, db, vaultKey, fek, hfk, embedMaxBytes)
        } catch (e: VaultOpenException) {
            throw e
        } catch (e: Exception) {
            throw VaultOpenException("unlock failed: ${e.message}")
        } finally {
            tmk.clear()
            smk.clear()
            macKey.clear()
            deviceSecret.clear()
            desktopSecret?.fill(0)
        }
    }

    // ------------------------------------------------------------------ steps

    /**
     * True when this vault can be opened with a fingerprint: the Keystore
     * BioKey exists *and* `vault.meta` carries the `vault_key:biokey` copy.
     */
    fun hasBiometricUnlock(vaultId: String = VaultStore.DEFAULT_VAULT_ID): Boolean {
        val metaFile = VaultStore.metaFile(context, vaultId)
        if (!metaFile.exists()) return false
        return BioKeyManager.hasBioKey() && runCatching {
            VaultMetaJson.decode(metaFile.readText()).wrappedKeys.any { it.id == WRAPPED_VAULT_KEY_BIOKEY }
        }.getOrDefault(false)
    }

    /**
     * Returns a biometric-bound DECRYPT cipher for `vault_key:biokey`; the
     * caller authenticates it via BiometricPrompt, then calls
     * [unlockWithBiometric] with the authorized cipher.
     */
    fun prepareBiometricUnlock(vaultId: String = VaultStore.DEFAULT_VAULT_ID): Cipher {
        val metaFile = VaultStore.metaFile(context, vaultId)
        if (!metaFile.exists()) throw VaultOpenException("not a vault: $vaultId")
        val meta = try {
            VaultMetaJson.decode(metaFile.readText())
        } catch (e: Exception) {
            throw VaultOpenException("corrupt vault.meta: ${e.message}")
        }
        val entry = meta.wrappedKeys.firstOrNull { it.id == WRAPPED_VAULT_KEY_BIOKEY }
            ?: throw VaultOpenException("no vault_key:biokey wrapped key in vault.meta")
        return try {
            BioKeyManager.createDecryptCipher(entry.nonce)
        } catch (e: Exception) {
            throw VaultOpenException("fingerprint key unavailable: ${e.message}")
        }
    }

    /**
     * Opens a vault with a biometric-authorized cipher (fingerprint "remember
     * me"). The wrapped `vault_key:biokey` is GCM-authenticated by its own tag,
     * so this path does not need the password-derived MAC key; a wrong or
     * invalidated key fails the GCM tag or the SQLCipher probe.
     */
    fun unlockWithBiometric(
        cipher: Cipher,
        vaultId: String = VaultStore.DEFAULT_VAULT_ID,
    ): VaultSession {
        val metaFile = VaultStore.metaFile(context, vaultId)
        if (!metaFile.exists()) throw VaultOpenException("not a vault: $vaultId")
        val meta = try {
            VaultMetaJson.decode(metaFile.readText())
        } catch (e: Exception) {
            throw VaultOpenException("corrupt vault.meta: ${e.message}")
        }
        // v3 is a fresh format: the schema is fixed at creation and old
        // (v1/v2) vaults are refused, matching the desktop client. There is
        // no in-place migration path.
        if (meta.version != META_VERSION) {
            throw VaultOpenException(
                "unsupported vault format version ${meta.version} — " +
                    "recreate the vault with this app (format v$META_VERSION)",
            )
        }
        val entry = meta.wrappedKeys.firstOrNull { it.id == WRAPPED_VAULT_KEY_BIOKEY }
            ?: throw VaultOpenException("no vault_key:biokey wrapped key in vault.meta")
        // BioKeySetup wraps the VaultKey with the Keystore cipher without AAD
        // (the Keystore key is already a dedicated wrapping key), so mirror it
        // here exactly: no updateAAD, tag appended to the ciphertext.
        val plain = try {
            cipher.doFinal(entry.cipherText + entry.authTag)
        } catch (e: Exception) {
            CryptoLog.e(C, "unlockWithBiometric: vault_key unwrap failed", e)
            throw VaultOpenException("fingerprint unlock failed")
        }
        val vaultKey = try {
            ProtectedValue.fromBinary(plain)
        } finally {
            plain.fill(0)
        }
        CryptoLog.d(C, "unlockWithBiometric: vault_key unwrapped vaultKey(${CryptoLog.key(vaultKey)})")
        return try {
            val db = openDatabase(vaultId, vaultKey)
            val fek = kdf.fileKey(vaultKey)
            val hfk = kdf.fingerprintKey(vaultKey)
            // config.sig cannot be verified on the biometric-only path (the
            // MAC key needs the master password); the VaultKey itself is
            // GCM-authenticated by the BioKey tag, and config.json carries
            // no secrets.
            val embedMaxBytes = runCatching {
                VaultMetaJson.embedMaxBytesFromConfig(VaultStore.configFile(context, vaultId).readBytes())
            }.getOrDefault(VaultMetaJson.DEFAULT_EMBED_MAX_BYTES)
            CryptoLog.d(C, "unlockWithBiometric: db opened fek(${CryptoLog.key(fek)}) " +
                "embed_max_bytes=$embedMaxBytes")
            VaultSession(vaultId, db, vaultKey, fek, hfk, embedMaxBytes)
        } catch (e: Exception) {
            vaultKey.clear()
            CryptoLog.e(C, "unlockWithBiometric failed", e)
            throw if (e is VaultOpenException) e else VaultOpenException("fingerprint unlock failed: ${e.message}")
        }
    }

    /**
     * Desktop `device_secret` files come in two legacy forms: 32 raw bytes or
     * 64 hex characters (docs/design.md "Device Secret on desktop"). Both must
     * be accepted for the one-time re-bind.
     */
    private fun readDesktopSecret(vaultId: String): ByteArray? {
        val file = VaultStore.deviceSecretFile(context, vaultId)
        if (!file.exists()) return null
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            throw VaultOpenException("corrupt device_secret file")
        }
        if (bytes.size == DEVICE_SECRET_SIZE) return bytes
        val text = String(bytes, Charsets.UTF_8).trim()
        return try {
            if (text.length != DEVICE_SECRET_SIZE * 2) {
                throw IllegalArgumentException("unexpected device_secret length")
            }
            text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) {
            bytes.fill(0)
            throw VaultOpenException("corrupt device_secret file")
        }
    }

    /** Verifies `config.sig` and returns `files.embed_max_bytes`. */
    private fun verifyConfig(meta: VaultMeta, macKey: ProtectedValue): Long {
        val configFile = VaultStore.configFile(context, meta.vaultId)
        val sigFile = VaultStore.configSigFile(context, meta.vaultId)
        if (!configFile.exists() || !sigFile.exists()) {
            throw VaultOpenException("missing config.json or config.sig")
        }
        val configBytes = configFile.readBytes()
        val expected = macKey.use { HmacSha256.mac(it, configBytes) }
        val stored = try {
            CrockfordBase32.decode(sigFile.readText().trim())
        } catch (e: Exception) {
            throw VaultOpenException("corrupt config.sig")
        }
        if (!MessageDigest.isEqual(stored, expected)) {
            throw VaultOpenException("config.sig mismatch — vault config tampered")
        }
        return VaultMetaJson.embedMaxBytesFromConfig(configBytes)
    }

    private fun verifyMetaMac(meta: VaultMeta, macKey: ProtectedValue) {
        val mac = meta.mac
        if (mac == null || mac.algorithm != "HMAC-SHA256") {
            throw VaultOpenException("vault.meta missing/invalid mac")
        }
        val expected = macKey.use { VaultMetaJson.computeMac(meta, it) }
        if (!MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), mac.value.toByteArray(Charsets.UTF_8))) {
            throw VaultOpenException("vault.meta mac mismatch — metadata tampered")
        }
    }

    private fun openDatabase(vaultId: String, vaultKey: ProtectedValue): SqlCipherDb {
        // A wrong key fails the SQLCipher page HMAC probe inside open();
        // a non-v3 schema fails the fixed-shape check.
        return VaultDatabase.open(context, VaultStore.dbFile(context, vaultId), vaultKey)
    }

    /**
     * Re-wraps `vault_key:smk` with the Android Keystore Device Secret and
     * deletes the desktop `device_secret` file — one-time binding on first
     * open of a desktop-created vault (docs/design.md "re-bind on import").
     *
     * The desktop `kek:smk` dev binding (if present) is re-wrapped with the
     * new SMK too: the KEK material does not depend on the Device Secret, but
     * its wrapper does. Keeping it alive lets the first Secret-tier access
     * import the KEK under the BioKey (see KekService) instead of losing the
     * DEKs wrapped by it.
     */
    private fun rebindVaultKey(
        meta: VaultMeta,
        vaultId: String,
        db: SqlCipherDb,
        oldSmk: ProtectedValue,
        masterPassword: ProtectedValue,
        vaultKey: ProtectedValue,
    ) {
        val androidSecret = DeviceSecretStore.getOrCreate(context)
        val tmk = kdf.createTransformedMasterKey(masterPassword, androidSecret, meta.salts.argon2, paramsOf(meta))
        val smk = kdf.createStretchedMasterKey(tmk, vaultId.toByteArray(Charsets.UTF_8), meta.salts.hkdf)
        try {
            val reWrapped = kdf.wrapKey(
                key = vaultKey,
                wrappingKey = kdf.vaultKey(smk),
                aad = WRAPPED_VAULT_KEY_SMK.toByteArray(Charsets.UTF_8),
            )
            val newEntry = VaultMeta.WrappedKeyEntry.fromWrappedKey(WRAPPED_VAULT_KEY_SMK, reWrapped)
            val updated = meta.copy(
                updatedAt = System.currentTimeMillis(),
                wrappedKeys = meta.wrappedKeys.map { if (it.id == WRAPPED_VAULT_KEY_SMK) newEntry else it },
            )
            rewrapDesktopKek(db, oldSmk, smk)
            val macBytes = kdf.macKey(smk).binaryValue()
            try {
                // config.sig is keyed by SMK[32:64], which changes with the
                // Device Secret — re-sign it for the Android binding or the
                // next unlock could never verify it.
                val configBytes = VaultStore.configFile(context, vaultId).readBytes()
                val newSig = CrockfordBase32.encode(HmacSha256.mac(macBytes, configBytes))
                configBytes.fill(0)
                val sigFile = VaultStore.configSigFile(context, vaultId)
                val tmpSig = File(sigFile.parentFile, "config.sig.tmp")
                tmpSig.writeText(newSig)
                if (!tmpSig.renameTo(sigFile)) {
                    tmpSig.delete()
                    throw VaultOpenException("failed to re-write config.sig")
                }
                val updatedWithMac = updated.copy(
                    mac = VaultMeta.Mac(
                        algorithm = "HMAC-SHA256",
                        value = VaultMetaJson.computeMac(updated, macBytes),
                    ),
                )
                val metaFile = VaultStore.metaFile(context, vaultId)
                val tmp = File(metaFile.parentFile, "vault.meta.tmp")
                tmp.writeText(VaultMetaJson.encode(updatedWithMac))
                if (!tmp.renameTo(metaFile)) {
                    tmp.delete()
                    throw VaultOpenException("failed to re-write vault.meta")
                }
            } finally {
                macBytes.fill(0)
            }
            VaultStore.deviceSecretFile(context, vaultId).delete()
        } finally {
            tmk.clear()
            smk.clear()
            androidSecret.clear()
        }
    }

    /**
     * Re-wraps a desktop `kek:smk` row with the new SMK (AAD stays `kek:smk`).
     * No-op when the row is absent or already migrated to `kek:biokey`.
     */
    private fun rewrapDesktopKek(
        db: SqlCipherDb,
        oldSmk: ProtectedValue,
        newSmk: ProtectedValue,
    ) {
        val row = db.rawQuery(
            "SELECT ciphertext, nonce, auth_tag FROM t_wrapped_key " +
                "WHERE id = 'kek:smk' AND deleted_at IS NULL LIMIT 1",
            null,
        ).use { c ->
            if (!c.moveToFirst()) null
            else Triple(c.getBlob(0), c.getBlob(1), c.getBlob(2))
        } ?: return

        val oldWrapping = kdf.vaultKey(oldSmk)
        val kek = try {
            kdf.unwrapKey(
                wrapped = com.typedefai.cryptowl.crypto.WrappedKey(row.first, row.second, row.third),
                wrappingKey = oldWrapping,
                aad = KEK_SMK_ID.toByteArray(Charsets.UTF_8),
            )
        } finally {
            oldWrapping.clear()
        }
        try {
            val newWrapping = kdf.vaultKey(newSmk)
            val wrapped = try {
                kdf.wrapKey(
                    key = kek,
                    wrappingKey = newWrapping,
                    aad = KEK_SMK_ID.toByteArray(Charsets.UTF_8),
                )
            } finally {
                newWrapping.clear()
            }
            db.execSQL(
                "UPDATE t_wrapped_key SET ciphertext = ?, nonce = ?, auth_tag = ?, updated_at = ? " +
                    "WHERE id = 'kek:smk'",
                arrayOf<Any?>(wrapped.cipherText, wrapped.nonce, wrapped.authTag, System.currentTimeMillis()),
            )
            CryptoLog.d(C, "rebind: kek:smk re-wrapped with the Android SMK")
        } finally {
            kek.clear()
        }
    }

    private fun paramsOf(meta: VaultMeta): KdfParams = KdfParams(
        algorithm = meta.kdf.algorithm,
        mCostKiB = meta.kdf.mKib,
        tCost = meta.kdf.t,
        parallelism = meta.kdf.p,
    )

    private companion object {
        private const val C = "UnlockService"
        const val META_VERSION = 3
        const val KEK_SMK_ID = "kek:smk"
        const val DEVICE_SECRET_SIZE = 32
        const val WRAPPED_VAULT_KEY_SMK = "vault_key:smk"
        const val WRAPPED_VAULT_KEY_BIOKEY = "vault_key:biokey"
    }
}
