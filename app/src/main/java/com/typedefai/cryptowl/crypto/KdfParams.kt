package com.typedefai.cryptowl.crypto

/**
 * Per-vault KDF parameters, recorded in `vault.meta` (`kdf` field) and read
 * back at derive time so parameters can be raised in future versions.
 * Stored values: algorithm, m (KiB), t, p — matching the vault.meta JSON.
 */
data class KdfParams(
    val algorithm: String = "argon2id",
    val mCostKiB: Int = 19456,
    val tCost: Int = 2,
    val parallelism: Int = 1,
) {
    companion object {
        /** OWASP-recommended minimum for Argon2id: m=19 MiB, t=2, p=1. */
        val OWASP = KdfParams()
    }
}
