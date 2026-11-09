package com.typedefai.cryptowl.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure auto-lock decision logic (no Android dependencies). */
class AutoLockPolicyTest {

    @Test
    fun immediateTimeoutLocksOnBackground() {
        assertTrue(AutoLockPolicy.shouldLockImmediately(AutoLockPolicy.IMMEDIATE))
        assertFalse(AutoLockPolicy.shouldLockImmediately(AutoLockPolicy.FIVE_MINUTES))
    }

    @Test
    fun resumeLocksOnlyAfterTheTimeout() {
        val timeout = AutoLockPolicy.FIVE_MINUTES
        assertFalse(AutoLockPolicy.shouldLockOnResume(elapsedMs = 0, timeoutMs = timeout))
        assertFalse(AutoLockPolicy.shouldLockOnResume(elapsedMs = timeout - 1, timeoutMs = timeout))
        assertTrue(AutoLockPolicy.shouldLockOnResume(elapsedMs = timeout, timeoutMs = timeout))
        assertTrue(AutoLockPolicy.shouldLockOnResume(elapsedMs = timeout * 3, timeoutMs = timeout))
    }

    @Test
    fun immediateTimeoutNeverUsesTheResumePath() {
        // locking already happened while backgrounding; resume must not re-lock
        assertFalse(AutoLockPolicy.shouldLockOnResume(elapsedMs = 60_000, timeoutMs = AutoLockPolicy.IMMEDIATE))
    }

    @Test
    fun optionsCoverTheDefault() {
        assertTrue(AutoLockPolicy.options.contains(AutoLockPolicy.DEFAULT))
    }
}
