package com.typedefai.cryptowl.crypto

import android.util.Log
import java.security.MessageDigest

/**
 * Central logging for the crypto/core paths. Every tag is `cwl:<Component>`,
 * so the whole chain is visible with `adb logcat | grep cwl:` — paths, KDF
 * parameters, salts, nonces, wrapped-key ids, key fingerprints and
 * verification outcomes.
 *
 * Raw key bytes are deliberately never logged (docs/design.md "Memory
 * Protection — no logs, no persistence"): [fingerprint] emits a short SHA-256
 * prefix that is stable across layers, so the same key can be correlated
 * (e.g. the VaultKey unwrapped at unlock vs. the one opening SQLCipher,
 * or the KEK before/after a wrap round-trip) without exposing it.
 */
object CryptoLog {

    const val PREFIX = "cwl"

    /**
     * Verbose tracing (paths, fingerprints, per-operation details) is enabled
     * in debug builds only: release builds must not leak vault paths or key
     * fingerprints into logcat. Errors are always logged.
     */
    private val verbose = com.typedefai.cryptowl.BuildConfig.DEBUG

    fun tag(component: String): String = "$PREFIX:$component"

    @JvmStatic
    fun d(component: String, message: String) {
        if (verbose) Log.d(tag(component), message)
    }

    @JvmStatic
    fun w(component: String, message: String) {
        if (verbose) Log.w(tag(component), message)
    }

    @JvmStatic
    @JvmOverloads
    fun e(component: String, message: String, t: Throwable? = null) {
        Log.e(tag(component), message, t)
    }

    /** Short, stable fingerprint (first 8 bytes of SHA-256, hex) — safe to log. */
    fun fingerprint(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = buildString(16) {
            for (i in 0 until 8) {
                append("0123456789abcdef"[(digest[i].toInt() shr 4) and 0xf])
                append("0123456789abcdef"[digest[i].toInt() and 0xf])
            }
        }
        digest.fill(0)
        return hex
    }

    fun fingerprint(value: ProtectedValue): String = value.use { fingerprint(it) }

    /** Standard key descriptor for log lines: `fp=<sha256-prefix> len=<bytes>`. */
    fun key(value: ProtectedValue): String = value.use { "fp=${fingerprint(it)} len=${it.size}" }

    /** Standard key descriptor for raw key bytes (does not retain the array). */
    fun key(bytes: ByteArray): String = "fp=${fingerprint(bytes)} len=${bytes.size}"
}
