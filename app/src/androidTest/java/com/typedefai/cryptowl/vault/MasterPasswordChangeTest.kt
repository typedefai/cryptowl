package com.typedefai.cryptowl.vault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.typedefai.cryptowl.crypto.ProtectedValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fast master-password change: old password is rejected afterwards, the new
 * one opens the vault, and both integrity signatures (config.sig + vault.meta
 * mac) were re-signed with the new MAC key — a missing re-sign would make the
 * vault open exactly once.
 */
@RunWith(AndroidJUnit4::class)
class MasterPasswordChangeTest {

    private lateinit var context: Context
    private val vaultId = "change-test-vault"
    private val oldPassword = ProtectedValue.fromString("old-password-123")
    private val newPassword = ProtectedValue.fromString("new-password-456")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        VaultStore.vaultDir(context, vaultId).deleteRecursively()
        VaultStore.indexFile(context).delete()
    }

    @Test
    fun changeRewrapsAndBothSignaturesVerify() {
        VaultCreator(context).create(oldPassword, vaultId)

        MasterPasswordChange(context).change(oldPassword, newPassword, vaultId)

        // old password is rejected
        try {
            UnlockService(context).unlock(oldPassword, vaultId).close()
            fail("old password must not open the vault after a change")
        } catch (expected: VaultOpenException) {
            // expected
        }

        // new password opens the vault (config.sig + meta mac verified inside)
        UnlockService(context).unlock(newPassword, vaultId).use { session ->
            assertEquals(4, session.db.version)
        }

        // and it still opens after a second independent derivation
        UnlockService(context).unlock(newPassword, vaultId).close()
    }

    @Test
    fun wrongCurrentPasswordIsRejectedAndVaultStaysUntouched() {
        VaultCreator(context).create(oldPassword, vaultId)

        try {
            MasterPasswordChange(context).change(ProtectedValue.fromString("wrong-current"), newPassword, vaultId)
            fail("expected the current-password check to fail")
        } catch (expected: VaultOpenException) {
            assertTrue(expected.message?.contains("current password") == true)
        }

        // original password still works
        UnlockService(context).unlock(oldPassword, vaultId).close()
    }
}
