package com.typedefai.cryptowl.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CrockfordBase32Test {

    private val data = "hello world!".toByteArray()

    @Test
    fun `encodes to grouped crockford string`() {
        assertEquals("D1JPR-V3F41-VPYWK-CCGGG", CrockfordBase32.encode(data))
    }

    @Test
    fun `decodes grouped string`() {
        assertArrayEquals(data, CrockfordBase32.decode("D1JP-RV3F-41VP-YWKC-CGGG"))
    }

    @Test
    fun `decodes ungrouped string`() {
        assertArrayEquals(data, CrockfordBase32.decode("D1JPRV3F41VPYWKCCGGG"))
    }

    @Test
    fun `decode accepts lowercase and aliases`() {
        assertArrayEquals(data, CrockfordBase32.decode("d1jprv3f41vpywkccggg"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws on invalid character`() {
        CrockfordBase32.decode("D1JPR*V3F41VPYWKCCGGG")
    }

    @Test
    fun `round trips arbitrary data`() {
        val random = RandomUtil.generateSecureBytes(33)
        assertArrayEquals(random, CrockfordBase32.decode(CrockfordBase32.encode(random)))
    }
}
