package com.typedefai.cryptowl.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomUtilTest {

    @Test
    fun `returns uuid of expected shape`() {
        val uuid = RandomUtil.generateUUID()
        assertEquals(36, uuid.length)
        assertTrue(uuid.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    @Test
    fun `uuids are distinct`() {
        assertNotEquals(RandomUtil.generateUUID(), RandomUtil.generateUUID())
    }

    @Test
    fun `returns random bytes with given length`() {
        assertEquals(32, RandomUtil.generateSecureBytes(32).size)
        assertNotEquals(
            RandomUtil.generateSecureBytes(32).toHex(),
            RandomUtil.generateSecureBytes(32).toHex(),
        )
    }
}
