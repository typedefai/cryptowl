package com.typedefai.cryptowl.vault

import android.content.Context
import com.typedefai.cryptowl.crypto.ProtectedValue
import java.io.File
import java.util.UUID

/**
 * Vault fixture for instrumented tests: creates a v3 vault under a unique id
 * (so tests never touch the user vault) and removes every artifact afterwards.
 */
object TestVaults {

    const val PASSWORD = "correct horse battery staple"

    fun create(context: Context): String {
        val vaultId = "test-${UUID.randomUUID()}"
        val password = ProtectedValue.fromString(PASSWORD)
        try {
            VaultCreator(context).create(password, vaultId)
        } finally {
            password.clear()
        }
        return vaultId
    }

    fun cleanup(context: Context, vaultId: String) {
        VaultStore.vaultDir(context, vaultId).deleteRecursively()
        val index = VaultStore.indexFile(context)
        if (index.exists() && index.readText().contains(vaultId)) index.delete()
    }

    fun unlock(context: Context, vaultId: String): VaultSession {
        val password = ProtectedValue.fromString(PASSWORD)
        try {
            return UnlockService(context).unlock(password, vaultId)
        } finally {
            password.clear()
        }
    }

    fun attachmentsDir(context: Context, vaultId: String): File =
        File(VaultStore.vaultDir(context, vaultId), "attachments")
}
