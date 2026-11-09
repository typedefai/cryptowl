package com.typedefai.cryptowl.crypto

import java.security.SecureRandom
import java.util.UUID

object RandomUtil {

    private val secureRandom = SecureRandom()

    fun generateSecureBytes(length: Int): ByteArray =
        ByteArray(length).also { secureRandom.nextBytes(it) }

    fun generateUUID(): String = UUID.randomUUID().toString()
}
