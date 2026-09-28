package com.typedefai.cryptowl.vault

import android.content.Context
import com.typedefai.cryptowl.crypto.KdfParams
import com.typedefai.cryptowl.crypto.KdfService
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.crypto.WrappedKey
import java.io.File
import javax.crypto.Cipher

/**
 * Optional fingerprint setup flow ("remember me"): wraps the VaultKey with
 * the BioKey (Keystore, biometric-bound) and adds the `vault_key:biokey`
 * copy to vault.meta. Runs only right after vault creation, while the master
 * password is still held by the onboarding flow.
 *
 * [prepare] derives the VaultKey and returns a biometric-bound encrypt
 * cipher; the caller must authenticate it via BiometricPrompt and then call
 * [complete] with the authorized cipher. Everything is wiped in [cancel].
 */
class BioKeySetup(
    private val context: Context,
    private val kdf: KdfService = KdfService(),
) {

    class Prepared internal constructor(
        internal val meta: VaultMeta,
        internal val vaultKey: ProtectedValue,
        internal val macKey: ProtectedValue,
        val cipher: Cipher,
    )

    fun prepare(
        masterPassword: ProtectedValue,
        vaultId: String = VaultStore.DEFAULT_VAULT_ID,
    ): Prepared {
        val metaFile = VaultStore.metaFile(context, vaultId)
        require(metaFile.exists()) { "vault.meta missing: $vaultId" }
        val meta = VaultMetaJson.decode(metaFile.readText())
        require(meta.version <= META_VERSION) { "unsupported vault.meta version: ${meta.version}" }

        val params = KdfParams(
            algorithm = meta.kdf.algorithm,
            mCostKiB = meta.kdf.mKib,
            tCost = meta.kdf.t,
            parallelism = meta.kdf.p,
        )
        val deviceSecret = DeviceSecretStore.getOrCreate(context)
        val tmk = kdf.createTransformedMasterKey(masterPassword, deviceSecret, meta.salts.argon2, params)
        val smk = kdf.createStretchedMasterKey(tmk, vaultId.toByteArray(Charsets.UTF_8), meta.salts.hkdf)
        val wrappedVaultKey = meta.wrappedKeys.firstOrNull { it.id == WRAPPED_VAULT_KEY_SMK }
            ?: error("vault_key:smk not found in vault.meta")
        val vaultKey = kdf.unwrapKey(
            wrapped = wrappedVaultKey.toWrappedKey(),
            wrappingKey = kdf.vaultKey(smk),
            aad = WRAPPED_VAULT_KEY_SMK.toByteArray(Charsets.UTF_8),
        )
        val macKey = kdf.macKey(smk)

        tmk.clear()
        smk.clear()
        deviceSecret.clear()

        BioKeyManager.ensureBioKey()
        val cipher = BioKeyManager.createEncryptCipher()
        return Prepared(meta, vaultKey, macKey, cipher)
    }

    /** Called with the biometric-authorized [cipher]; persists vault_key:biokey. */
    fun complete(prepared: Prepared, cipher: Cipher): VaultMeta {
        try {
            val encrypted = cipher.doFinal(prepared.vaultKey.binaryValue())
            val wrapped = WrappedKey(
                cipherText = encrypted.copyOf(encrypted.size - TAG_SIZE),
                nonce = cipher.iv,
                authTag = encrypted.copyOfRange(encrypted.size - TAG_SIZE, encrypted.size),
            )
            val entry = VaultMeta.WrappedKeyEntry.fromWrappedKey(WRAPPED_VAULT_KEY_BIOKEY, wrapped)
            // Replace an existing copy: re-enrolling after a fingerprint change
            // must not leave a stale (undecryptable) entry behind.
            val updated = prepared.meta.copy(
                updatedAt = System.currentTimeMillis(),
                wrappedKeys = prepared.meta.wrappedKeys.filterNot { it.id == WRAPPED_VAULT_KEY_BIOKEY } + entry,
            )
            val macKeyBytes = prepared.macKey.binaryValue()
            val updatedWithMac = updated.copy(
                mac = VaultMeta.Mac(
                    algorithm = "HMAC-SHA256",
                    value = VaultMetaJson.computeMac(updated, macKeyBytes),
                ),
            )
            macKeyBytes.fill(0)

            val metaFile = VaultStore.metaFile(context, updated.vaultId)
            val tmp = File(metaFile.parentFile, "vault.meta.tmp")
            tmp.writeText(VaultMetaJson.encode(updatedWithMac))
            if (!tmp.renameTo(metaFile)) {
                tmp.delete()
                throw IllegalStateException("failed to write vault.meta atomically")
            }
            return updatedWithMac
        } finally {
            prepared.vaultKey.clear()
            prepared.macKey.clear()
        }
    }

    /** Clears the derived keys when the flow is aborted or the user skips. */
    fun cancel(prepared: Prepared) {
        prepared.vaultKey.clear()
        prepared.macKey.clear()
    }

    private companion object {
        const val META_VERSION = 2
        const val TAG_SIZE = 16
        const val WRAPPED_VAULT_KEY_SMK = "vault_key:smk"
        const val WRAPPED_VAULT_KEY_BIOKEY = "vault_key:biokey"
    }
}
