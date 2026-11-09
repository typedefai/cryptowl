package com.typedefai.cryptowl.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HmacSha256Test {

    private val key = "509f825b859521f72fe511d2c120f53ed52bf641932d92ba086b89be3d65153a".hexToBytes()
    private val message = "41964e60-5fc3-472c-8b87-71363c71b03c".toByteArray()

    @Test
    fun `returns hmac-sha256 hash`() {
        val hash = HmacSha256.mac(key, message)
        assertEquals(32, hash.size)
        assertEquals("d14d4dca4b89d24cf80b731320b57b3b94efe47e6b19972d40a5914b7053d5d7", hash.toHex())
    }

    @Test
    fun `same input yields same output`() {
        assertArrayEquals(HmacSha256.mac(key, message), HmacSha256.mac(key, message))
    }
}
