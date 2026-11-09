package com.typedefai.cryptowl.crypto

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectedValueTest {

    @Test
    fun `returns original value from string`() {
        val value = ProtectedValue.fromString("hello world!")
        assertArrayEquals("hello world!".toByteArray(StandardCharsets.UTF_8), value.binaryValue())
    }

    @Test
    fun `returns original value from binary`() {
        val bytes = "3f09ea13ceffb8e867a4af3ab17854f9f5f152591653c737a8962b94356e2c0f".hexToBytes()
        val value = ProtectedValue.fromBinary(bytes)
        assertArrayEquals(bytes, value.binaryValue())
    }

    @Test
    fun `returns crockford text`() {
        val value = ProtectedValue.fromString("hello world!")
        assertEquals("D1JPR-V3F41-VPYWK-CCGGG", value.getText())
    }

    @Test
    fun `does not expose backing buffer`() {
        val original = "secret".toByteArray()
        val value = ProtectedValue.fromBinary(original)
        original.fill(0)
        assertArrayEquals("secret".toByteArray(), value.binaryValue())
    }

    @Test
    fun `clear scrubs the buffer`() {
        val value = ProtectedValue.fromString("secret")
        value.clear()
        assertArrayEquals(ByteArray(6), value.binaryValue())
    }

    @Test
    fun `use provides auto-cleared scoped access`() {
        val value = ProtectedValue.fromString("secret")
        val length = value.use { bytes ->
            assertArrayEquals("secret".toByteArray(), bytes)
            bytes.size
        }
        assertEquals(6, length)
        assertArrayEquals("secret".toByteArray(), value.binaryValue())
    }

    @Test
    fun `use after clear yields zeros`() {
        val value = ProtectedValue.fromString("secret")
        value.clear()
        value.use { bytes ->
            assertEquals(ByteArray(6).toList(), bytes.toList())
        }
    }

    @Test
    fun `toString is masked`() {
        assertEquals("[ProtectedValue]", ProtectedValue.fromString("secret").toString())
    }

    @Test
    fun `getText works via scoped access`() {
        val value = ProtectedValue.fromString("hello world!")
        assertEquals("D1JPR-V3F41-VPYWK-CCGGG", value.getText())
        assertArrayEquals("hello world!".toByteArray(), value.binaryValue())
    }
}
