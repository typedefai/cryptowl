package com.typedefai.cryptowl.vault

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.typedefai.cryptowl.crypto.ProtectedValue
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fast master-password change: re-wraps `vault_key:smk` and re-signs BOTH
 * integrity signatures (`config.sig` + `vault.meta` mac) with the new MAC key.
 */
@RunWith(AndroidJUnit4::class)
class MasterPasswordChangeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var vaultId: String

    @Before
    fun setUp() {
        vaultId = TestVaults.create(context)
    }

    @After
    fun tearDown() {
        TestVaults.cleanup(context, vaultId)
    }

    @Test
    fun changes_password_and_resigns_both_macs() {
        val oldPassword = ProtectedValue.fromString(TestVaults.PASSWORD)
        val newPassword = ProtectedValue.fromString("a brand new master password")
        try {
            MasterPasswordChange(context).change(oldPassword, newPassword, vaultId)
        } finally {
            oldPassword.clear()
        }

        // Old password must fail (meta MAC mismatch), new one must open.
        val stale = ProtectedValue.fromString(TestVaults.PASSWORD)
        try {
            var rejected = false
            try {
                UnlockService(context).unlock(stale, vaultId)
            } catch (e: VaultOpenException) {
                rejected = true
            }
            assertTrue(rejected)
        } finally {
            stale.clear()
        }

        val session = UnlockService(context).unlock(newPassword, vaultId)
        try {
            assertTrue(session.vaultKey.use { it.size } == 32)
        } finally {
            session.close()
            newPassword.clear()
        }
    }
}
