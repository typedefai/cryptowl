package com.typedefai.cryptowl.crypto

/** HKDF-SHA256 (RFC 5869): extract-then-expand key derivation. */
object Hkdf {

    private const val HASH_LENGTH = 32

    fun deriveKey(
        ikm: ByteArray,
        salt: ByteArray = ByteArray(0),
        info: ByteArray = ByteArray(0),
        outputLength: Int = 64,
    ): ByteArray {
        require(outputLength in 1..(255 * HASH_LENGTH)) { "outputLength too large" }

        val effectiveSalt = if (salt.isEmpty()) ByteArray(HASH_LENGTH) else salt
        val prk = HmacSha256.mac(effectiveSalt, ikm)

        val result = ByteArray(outputLength)
        var t = ByteArray(0)
        var position = 0
        var counter = 1
        while (position < outputLength) {
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(javax.crypto.spec.SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()

            val count = minOf(t.size, outputLength - position)
            t.copyInto(result, position, 0, count)
            position += count
            counter++
        }
        return result
    }
}
