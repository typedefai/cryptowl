package com.typedefai.cryptowl.vault

import android.content.Context
import android.util.Log
import com.typedefai.cryptowl.crypto.CrockfordBase32
import com.typedefai.cryptowl.crypto.HmacSha256
import com.typedefai.cryptowl.crypto.KdfParams
import com.typedefai.cryptowl.crypto.KdfService
import com.typedefai.cryptowl.crypto.ProtectedValue
import java.io.File

/**
 * Fast master-password change (docs/design.md "Master Password Change"):
 * envelope encryption means only `vault_key:smk` is rewrapped — the SQLCipher
 * key never changes, no `PRAGMA rekey`, no data re-encryption.
 *
 * The MAC key (`SMK[32:64]`) depends on the password, so BOTH integrity
 * signatures must be re-signed with the new key: `config.sig` and the
 * `vault.meta` mac — the same trap documented for the desktop re-bind.
 * `vault_key:biokey` is untouched (the Keystore BioKey is password-independent).
 */
class MasterPasswordChange(
    private val context: Context,
    private val kdf: KdfService = KdfService(),
) {

    fun change(
        currentPassword: ProtectedValue,
        newPassword: ProtectedValue,
        vaultId: String = VaultStore.DEFAULT_VAULT_ID,
    ): VaultMeta {
        val metaFile = VaultStore.metaFile(context, vaultId)
        if (!metaFile.exists()) throw VaultOpenException("not a vault: $vaultId")
        val meta = try {
            VaultMetaJson.decode(metaFile.readText())
        } catch (e: Exception) {
            throw VaultOpenException("corrupt vault.meta: ${e.message}")
        }
        if (meta.version > META_VERSION) {
            throw VaultOpenException("unsupported vault.meta version: ${meta.version}")
        }
        val params = KdfParams(
            algorithm = meta.kdf.algorithm,
            mCostKiB = meta.kdf.mKib,
            tCost = meta.kdf.t,
            parallelism = meta.kdf.p,
        )
        val deviceSecret = DeviceSecretStore.getOrCreate(context)
        val oldTmk = kdf.createTransformedMasterKey(currentPassword, deviceSecret, meta.salts.argon2, params)
        val oldSmk = kdf.createStretchedMasterKey(oldTmk, vaultId.toByteArray(Charsets.UTF_8), meta.salts.hkdf)
        val entry = meta.wrappedKeys.firstOrNull { it.id == WRAPPED_VAULT_KEY_SMK }
            ?: throw VaultOpenException("no vault_key:smk wrapped key in vault.meta")
        val vaultKey = try {
            kdf.unwrapKey(
                wrapped = entry.toWrappedKey(),
                wrappingKey = kdf.vaultKey(oldSmk),
                aad = WRAPPED_VAULT_KEY_SMK.toByteArray(Charsets.UTF_8),
            )
        } catch (e: Exception) {
            Log.e(TAG, "change: current password rejected", e)
            throw VaultOpenException("current password is incorrect")
        }

        try {
            val newTmk = kdf.createTransformedMasterKey(newPassword, deviceSecret, meta.salts.argon2, params)
            val newSmk = kdf.createStretchedMasterKey(newTmk, vaultId.toByteArray(Charsets.UTF_8), meta.salts.hkdf)
            try {
                val reWrapped = kdf.wrapKey(
                    key = vaultKey,
                    wrappingKey = kdf.vaultKey(newSmk),
                    aad = WRAPPED_VAULT_KEY_SMK.toByteArray(Charsets.UTF_8),
                )
                val newEntry = VaultMeta.WrappedKeyEntry.fromWrappedKey(WRAPPED_VAULT_KEY_SMK, reWrapped)
                val updated = meta.copy(
                    updatedAt = System.currentTimeMillis(),
                    wrappedKeys = meta.wrappedKeys.map { if (it.id == WRAPPED_VAULT_KEY_SMK) newEntry else it },
                )
                val macKeyBytes = kdf.macKey(newSmk).binaryValue()
                val updatedWithMac: VaultMeta
                try {
                    resignConfig(vaultId, macKeyBytes)
                    updatedWithMac = updated.copy(
                        mac = VaultMeta.Mac(
                            algorithm = "HMAC-SHA256",
                            value = VaultMetaJson.computeMac(updated, macKeyBytes),
                        ),
                    )
                    writeMetaAtomically(updatedWithMac)
                } finally {
                    macKeyBytes.fill(0)
                }
                Log.d(TAG, "change: vault_key:smk rewrapped, config.sig + meta mac re-signed")
                return updatedWithMac
            } finally {
                newTmk.clear()
                newSmk.clear()
            }
        } finally {
            vaultKey.clear()
            oldTmk.clear()
            oldSmk.clear()
            deviceSecret.clear()
        }
    }

    private fun resignConfig(vaultId: String, macKeyBytes: ByteArray) {
        val configBytes = VaultStore.configFile(context, vaultId).readBytes()
        try {
            val newSig = CrockfordBase32.encode(HmacSha256.mac(macKeyBytes, configBytes))
            val sigFile = VaultStore.configSigFile(context, vaultId)
            val tmpSig = File(sigFile.parentFile, "config.sig.tmp")
            tmpSig.writeText(newSig)
            if (!tmpSig.renameTo(sigFile)) {
                tmpSig.delete()
                throw VaultOpenException("failed to re-write config.sig")
            }
        } finally {
            configBytes.fill(0)
        }
    }

    private fun writeMetaAtomically(meta: VaultMeta) {
        val metaFile = VaultStore.metaFile(context, meta.vaultId)
        val tmp = File(metaFile.parentFile, "vault.meta.tmp")
        tmp.writeText(VaultMetaJson.encode(meta))
        if (!tmp.renameTo(metaFile)) {
            tmp.delete()
            throw VaultOpenException("failed to re-write vault.meta")
        }
    }

    private companion object {
        const val TAG = "cwl:MasterPasswordChange"
        const val META_VERSION = 2
        const val WRAPPED_VAULT_KEY_SMK = "vault_key:smk"
    }
}
