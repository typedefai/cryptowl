package com.typedefai.cryptowl.vault

import android.content.Context
import java.io.File

/**
 * Vault storage layout on disk — see docs/design.md "Vault Storage Layout".
 *
 *   <filesDir>/vaults/<vaultId>/vault.meta, vault.db, config.json, ...
 *   <filesDir>/vault_index.json  (list of vault IDs and display names)
 */
object VaultStore {

    const val DEFAULT_VAULT_ID = "personal"

    fun vaultsDir(context: Context): File = File(context.filesDir, "vaults")

    fun vaultDir(context: Context, vaultId: String = DEFAULT_VAULT_ID): File =
        File(vaultsDir(context), vaultId)

    fun metaFile(context: Context, vaultId: String = DEFAULT_VAULT_ID): File =
        File(vaultDir(context, vaultId), "vault.meta")

    fun configFile(context: Context, vaultId: String = DEFAULT_VAULT_ID): File =
        File(vaultDir(context, vaultId), "config.json")

    fun configSigFile(context: Context, vaultId: String = DEFAULT_VAULT_ID): File =
        File(vaultDir(context, vaultId), "config.sig")

    fun deviceSecretFile(context: Context, vaultId: String = DEFAULT_VAULT_ID): File =
        File(vaultDir(context, vaultId), "device_secret")

    fun dbFile(context: Context, vaultId: String = DEFAULT_VAULT_ID): File =
        File(vaultDir(context, vaultId), "vault.db")

    fun indexFile(context: Context): File = File(context.filesDir, "vault_index.json")

    /**
     * True once the vault's meta and database files exist — the artifacts are
     * the ground truth for onboarding. The index is written *last* during
     * creation, so a crashed attempt leaves the artifacts but not the index;
     * keying off the index would wrongly show onboarding again.
     */
    fun isOnboarded(context: Context, vaultId: String = DEFAULT_VAULT_ID): Boolean =
        metaFile(context, vaultId).exists() && dbFile(context, vaultId).exists()

    /** The vault the app opens on start (first in the index), or null. */
    fun primaryVaultId(context: Context): String? {
        val index = indexFile(context)
        if (!index.exists()) return null
        val content = index.readText()
        val match = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(content)
        return match?.groupValues?.get(1)
    }

    fun writeIndex(context: Context, vaultId: String) {
        val index = indexFile(context)
        index.parentFile?.mkdirs()
        val tmp = File(index.parentFile, index.name + ".tmp")
        tmp.writeText("""{"vaults":[{"id":"$vaultId"}]}""")
        if (!tmp.renameTo(index)) {
            tmp.delete()
            index.writeText("""{"vaults":[{"id":"$vaultId"}]}""")
        }
    }
}
