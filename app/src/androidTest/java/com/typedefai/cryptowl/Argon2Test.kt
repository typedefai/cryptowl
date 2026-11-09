package com.typedefai.cryptowl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.typedefai.cryptowl.crypto.Argon2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Argon2 JNI binding tests.
 *
 * Vectors are the official ones from the reference implementation's test suite:
 * password = "password", salt = "somesalt", t = 2, m = 65536 KiB, p = 4, hashLen = 24.
 * (Also used by cryptowl-ref's native_argon2 Dart FFI tests.)
 */
@RunWith(AndroidJUnit4::class)
class Argon2Test {

    private val password = "password".toByteArray()
    private val salt = "somesalt".toByteArray()

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun base64(bytes: ByteArray): String =
        java.util.Base64.getEncoder().encodeToString(bytes)

    // --- Raw hash vectors (hashLen = 24) ---

    @Test
    fun argon2iRawVector() {
        val hash = Argon2.hash(password, salt, mCost = 65536, tCost = 2, parallelism = 4, hashLen = 24, type = Argon2.Type.ARGON2i)
        assertEquals("RdescudvJCsgt3ub+b+dWRWJTmaaJObG", base64(hash))
    }

    @Test
    fun argon2dRawVector() {
        val hash = Argon2.hash(password, salt, mCost = 65536, tCost = 2, parallelism = 4, hashLen = 24, type = Argon2.Type.ARGON2d)
        assertEquals("7Kn6V2imUuaFkZmKdZLb3nvg91N5Lt7H", base64(hash))
    }

    @Test
    fun argon2idRawVector() {
        val hash = Argon2.hash(password, salt, mCost = 65536, tCost = 2, parallelism = 4, hashLen = 24)
        assertEquals("F1jG2CV3/Nr+yRuIsPKw0J9r4s7cJHBU", base64(hash))
    }

    @Test
    fun argon2idVector32Byte() {
        val hash = Argon2.hash(password, salt, mCost = 65536, tCost = 2)
        assertEquals("09316115d5cf24ed5a15a31a3ba326e5cf32edc24702987c02b6566f61913cf7", hex(hash))
    }

    @Test
    fun argon2iVector32Byte() {
        val hash = Argon2.hash(password, salt, mCost = 65536, tCost = 2, type = Argon2.Type.ARGON2i)
        assertEquals("c1628832147d9720c5bd1cfd61367078729f6dfb6f8fea9ff98158e0d7816ed0", hex(hash))
    }

    // --- Encoded hash vectors ---

    @Test
    fun argon2iEncodedVector() {
        val encoded = Argon2.hashEncoded(password, salt, mCost = 65536, tCost = 2, parallelism = 4, hashLen = 24, type = Argon2.Type.ARGON2i)
        assertEquals("\$argon2i\$v=19\$m=65536,t=2,p=4\$c29tZXNhbHQ\$RdescudvJCsgt3ub+b+dWRWJTmaaJObG", encoded)
    }

    @Test
    fun argon2dEncodedVector() {
        val encoded = Argon2.hashEncoded(password, salt, mCost = 65536, tCost = 2, parallelism = 4, hashLen = 24, type = Argon2.Type.ARGON2d)
        assertEquals("\$argon2d\$v=19\$m=65536,t=2,p=4\$c29tZXNhbHQ\$7Kn6V2imUuaFkZmKdZLb3nvg91N5Lt7H", encoded)
    }

    @Test
    fun argon2idEncodedVector() {
        val encoded = Argon2.hashEncoded(password, salt, mCost = 65536, tCost = 2, parallelism = 4, hashLen = 24)
        assertEquals("\$argon2id\$v=19\$m=65536,t=2,p=4\$c29tZXNhbHQ\$F1jG2CV3/Nr+yRuIsPKw0J9r4s7cJHBU", encoded)
    }

    @Test
    fun encodedAndVerifyRoundTrip() {
        val encoded = Argon2.hashEncoded(password, salt, mCost = 256, tCost = 2)
        assertEquals("\$argon2id\$v=19\$m=256,t=2,p=1\$c29tZXNhbHQ\$nf65EOgLrQMR/uIPnA4rEsF5h7TKyQwu9U1bMCHGi/4", encoded)

        assertTrue(Argon2.verify(encoded, password))
        assertFalse(Argon2.verify(encoded, "wrong password".toByteArray()))
    }

    @Test
    fun hashLenIsRespected() {
        val hash = Argon2.hash(password, salt, mCost = 256, tCost = 2, hashLen = 16)
        assertEquals(16, hash.size)
    }

    @Test
    fun invalidParametersThrow() {
        try {
            Argon2.hash(password, salt, mCost = 0, tCost = 0)
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
    }
}
