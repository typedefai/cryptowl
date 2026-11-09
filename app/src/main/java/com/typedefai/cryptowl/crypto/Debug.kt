package com.typedefai.cryptowl.crypto

/**
 * Debug helpers for inspecting the crypto/vault flow. Only non-secret
 * material (sizes, params, salts, ciphertext) is ever logged — see the
 * AGENTS.md error-handling rules; never log [ProtectedValue] contents.
 */
fun ByteArray.toHexString(maxBytes: Int = Int.MAX_VALUE): String =
    take(maxBytes).joinToString("") { "%02x".format(it) } +
        if (size > maxBytes) "…(total ${size}B)" else ""
