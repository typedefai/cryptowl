package com.typedefai.cryptowl.settings

/**
 * Auto-lock semantics, kept pure so they can be unit-tested on the JVM.
 *
 * A vault with an unlocked session locks when the app goes to the background
 * (immediately for [IMMEDIATE], else after the timeout), and again on resume
 * if the app was frozen past the timeout while backgrounded.
 */
object AutoLockPolicy {

    const val IMMEDIATE = 0L
    const val ONE_MINUTE = 60_000L
    const val FIVE_MINUTES = 300_000L
    const val THIRTY_MINUTES = 1_800_000L

    const val DEFAULT = FIVE_MINUTES

    val options: List<Long> = listOf(IMMEDIATE, ONE_MINUTE, FIVE_MINUTES, THIRTY_MINUTES)

    fun shouldLockImmediately(timeoutMs: Long): Boolean = timeoutMs == IMMEDIATE

    fun shouldLockOnResume(elapsedMs: Long, timeoutMs: Long): Boolean =
        timeoutMs > IMMEDIATE && elapsedMs >= timeoutMs
}
