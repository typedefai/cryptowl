package com.typedefai.cryptowl.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HmacSha256 {
    private const val ALGORITHM = "HmacSHA256"

    fun mac(key: ByteArray, message: ByteArray): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(key, ALGORITHM))
        return mac.doFinal(message)
    }
}
