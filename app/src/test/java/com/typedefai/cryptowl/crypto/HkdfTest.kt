package com.typedefai.cryptowl.crypto

import org.junit.Assert.assertEquals
import org.junit.Test

class HkdfTest {

    private val ikm = "509f825b859521f72fe511d2c120f53ed52bf641932d92ba086b89be3d65153a".hexToBytes()
    private val salt = "b27f6e2bd596308c190c4f1d68660bc3".hexToBytes()
    private val info = "41964e60-5fc3-472c-8b87-71363c71b03c".toByteArray()

    @Test
    fun `returns 64-byte stretched key`() {
        val stretched = Hkdf.deriveKey(ikm = ikm, salt = salt, info = info)
        assertEquals(64, stretched.size)
        assertEquals(
            "6afa653ec25489cf4501713b2d97293361dcc492f05de076ee95a5033ff81682" +
                "588a599a45b9110676cf76e421548013e4e289f305918ba31bd6e24f227d67c8",
            stretched.toHex(),
        )
    }

    @Test
    fun `honors requested output length`() {
        assertEquals(32, Hkdf.deriveKey(ikm = ikm, salt = salt, info = info, outputLength = 32).size)
        assertEquals(16, Hkdf.deriveKey(ikm = ikm, salt = salt, info = info, outputLength = 16).size)
    }

    @Test
    fun `works without salt and info`() {
        val derived = Hkdf.deriveKey(ikm = ikm)
        assertEquals(64, derived.size)
    }
}
