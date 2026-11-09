package com.typedefai.cryptowl.vault

import com.typedefai.cryptowl.crypto.CrockfordBase32
import com.typedefai.cryptowl.crypto.HmacSha256
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VaultMetaJsonTest {

    private val meta = VaultMeta(
        version = 2,
        vaultId = "personal",
        createdAt = 1780000000000,
        updatedAt = 1780000000001,
        kdf = VaultMeta.Kdf(algorithm = "argon2id", mKib = 19456, t = 2, p = 1),
        salts = VaultMeta.Salts(
            argon2 = byteArrayOf(0x01, 0x02, 0x03, 0x04),
            hkdf = byteArrayOf(0x05, 0x06, 0x07, 0x08),
            secondary = byteArrayOf(0x09, 0x0a),
        ),
        wrappedKeys = listOf(
            VaultMeta.WrappedKeyEntry(
                id = "vault_key:smk",
                role = "vault_key",
                wrapper = "smk",
                algorithm = "AES-256-GCM",
                cipherText = byteArrayOf(0x11, 0x12),
                nonce = byteArrayOf(0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2a, 0x2b, 0x2c),
                authTag = byteArrayOf(0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a, 0x3b, 0x3c, 0x3d, 0x3e, 0x3f, 0x40),
            ),
        ),
    )

    @Test
    fun `encodes canonical json with lexicographic keys and base32 binary fields`() {
        val json = VaultMetaJson.encode(
            meta.copy(mac = VaultMeta.Mac(algorithm = "HMAC-SHA256", value = "MACVALUE")),
        )
        // Lexicographic order at every level, exactly like json.dumps(sort_keys=True).
        assertEquals(
            "{\"created_at\":1780000000000," +
                "\"kdf\":{\"algorithm\":\"argon2id\",\"m_kib\":19456,\"p\":1,\"t\":2}," +
                "\"mac\":{\"algorithm\":\"HMAC-SHA256\",\"value\":\"MACVALUE\"}," +
                "\"salts\":{\"argon2\":\"04106-10\",\"hkdf\":\"0M30E-20\",\"secondary\":\"1450\"}," +
                "\"updated_at\":1780000000001,\"vault_id\":\"personal\",\"version\":2," +
                "\"wrapped_keys\":[{\"algorithm\":\"AES-256-GCM\",\"auth_tag\":\"64S36-D1N6R-VKGE9-T7CY3-TFHZ8-0\"," +
                "\"ciphertext\":\"2490\",\"id\":\"vault_key:smk\",\"nonce\":\"44H26-9154R-KJGA9-A5CP0\"," +
                "\"role\":\"vault_key\",\"wrapper\":\"smk\"}]}",
            json,
        )
    }

    /**
     * Byte-exact cross-verification with the desktop oracle: `oracle_canonical_meta.json`
     * is the exact output of python `json.dumps(meta, sort_keys=True, separators=(",", ":"))`
     * for the fixed vectors in `wechat_sns_export/tests/test_vaultlib.py`. Encoding the
     * parsed meta must reproduce it byte-for-byte, and the MAC must match
     * VECTOR_META_MAC (742da830…).
     */
    @Test
    fun `byte-exact with the vaultlib oracle canonical json`() {
        val oracleJson = File("src/test/resources/vectors/oracle_canonical_meta.json").readText()

        val decoded = VaultMetaJson.decode(oracleJson)

        // round trip must reproduce the oracle bytes exactly
        assertEquals(oracleJson, VaultMetaJson.encode(decoded))

        // fields from the fixed vectors
        assertEquals(2, decoded.version)
        assertEquals("personal", decoded.vaultId)
        assertArrayEquals(ByteArray(32) { 0x22 }, decoded.salts.argon2)
        assertArrayEquals(ByteArray(32) { 0x33 }, decoded.salts.hkdf)
        assertNull(decoded.salts.secondary)
        val wrapped = decoded.wrappedKeys.single()
        assertEquals("vault_key:smk", wrapped.id)
        assertArrayEquals(ByteArray(32) { 0x44 }, wrapped.toWrappedKey().let { unwrapSmk(it) })
        assertEquals("HMAC-SHA256", decoded.mac?.algorithm)

        // MAC verification with SMK[32:64] (oracle vector) == VECTOR_META_MAC
        val smk = decodedSmk
        val macKey = smk.copyOfRange(32, 64)
        val expected = HmacSha256.mac(macKey, VaultMetaJson.canonicalWithoutMac(decoded).toByteArray(Charsets.UTF_8))
        assertArrayEquals(
            "742da83099511ccdf75578474dbe99c9d18843b6ef1f087cb460264272f07f9e".hexToBytes(),
            expected,
        )
        assertEquals(CrockfordBase32.encode(expected), decoded.mac?.value)
        assertEquals(CrockfordBase32.encode(expected), VaultMetaJson.computeMac(decoded, macKey))
    }

    @Test
    fun `decode round trips encode`() {
        val withMac = meta.copy(mac = VaultMeta.Mac(algorithm = "HMAC-SHA256", value = "SOMEMAC"))
        val decoded = VaultMetaJson.decode(VaultMetaJson.encode(withMac))

        assertEquals(withMac.version, decoded.version)
        assertEquals(withMac.vaultId, decoded.vaultId)
        assertEquals(withMac.createdAt, decoded.createdAt)
        assertEquals(withMac.updatedAt, decoded.updatedAt)
        assertEquals(withMac.kdf, decoded.kdf)
        assertArrayEquals(withMac.salts.argon2, decoded.salts.argon2)
        assertArrayEquals(withMac.salts.hkdf, decoded.salts.hkdf)
        assertArrayEquals(withMac.salts.secondary, decoded.salts.secondary)
        assertEquals(withMac.wrappedKeys.size, decoded.wrappedKeys.size)
        val original = withMac.wrappedKeys.first()
        val parsed = decoded.wrappedKeys.first()
        assertEquals(original.id, parsed.id)
        assertEquals(original.role, parsed.role)
        assertEquals(original.wrapper, parsed.wrapper)
        assertEquals(original.algorithm, parsed.algorithm)
        assertArrayEquals(original.cipherText, parsed.cipherText)
        assertArrayEquals(original.nonce, parsed.nonce)
        assertArrayEquals(original.authTag, parsed.authTag)
        assertEquals(withMac.mac, decoded.mac)
    }

    @Test
    fun `decode tolerates missing optional fields`() {
        val json = VaultMetaJson.encode(meta.copy(salts = meta.salts.copy(secondary = null), mac = null))
        val decoded = VaultMetaJson.decode(json)

        assertNull(decoded.salts.secondary)
        assertNull(decoded.mac)
    }

    @Test
    fun `decode handles whitespace and string numbers`() {
        val withSpace = VaultMetaJson.encode(meta)
            .replace("\"version\":2", "\"version\": \"2\"")
            .replace("{", "{\n  ")
            .replace(",", ",\n  ")
            .replace("}", "\n}")
        val decoded = VaultMetaJson.decode(withSpace)

        assertEquals(2, decoded.version)
        assertEquals(19456, decoded.kdf.mKib)
    }

    @Test
    fun `mac is hmac-sha256 over canonical json without the mac field`() {
        val macKey = "509f825b859521f72fe511d2c120f53ed52bf641932d92ba086b89be3d65153a".hexToBytes()
        val withoutMac = VaultMetaJson.canonicalWithoutMac(meta)

        val expected = HmacSha256.mac(macKey, withoutMac.toByteArray(Charsets.UTF_8))
        val expectedBase32 = CrockfordBase32.encode(expected)

        assertEquals(expectedBase32, VaultMetaJson.computeMac(meta, macKey))
    }

    @Test
    fun `mac changes when meta content changes`() {
        val macKey = ByteArray(32) { 0x42 }
        val macA = VaultMetaJson.computeMac(meta, macKey)
        val macB = VaultMetaJson.computeMac(meta.copy(updatedAt = meta.updatedAt + 1), macKey)
        val macC = VaultMetaJson.computeMac(
            meta.copy(wrappedKeys = meta.wrappedKeys + meta.wrappedKeys.first()), macKey,
        )
        assertEquals(macA, VaultMetaJson.computeMac(meta, macKey))
        assertEquals(false, macA == macB)
        assertEquals(false, macA == macC)
    }

    @Test
    fun `writes non-ascii as raw utf-8 like qt`() {
        val metaWithUnicode = meta.copy(vaultId = "个人")
        val json = VaultMetaJson.encode(metaWithUnicode)

        // Qt's QJsonDocument::toJson emits raw UTF-8, not \uXXXX escapes —
        // desktop vaults with non-ASCII ids depend on this for MAC checks.
        assertEquals(true, json.contains("\"vault_id\":\"个人\""))
        assertEquals(false, json.contains("\\u4e2a"))
        // round trip
        assertEquals("个人", VaultMetaJson.decode(json).vaultId)
    }

    @Test
    fun `control characters use the short json escapes`() {
        val json = VaultMetaJson.encode(meta.copy(vaultId = "a\nb\tc\u0001"))

        assertEquals(true, json.contains("\"vault_id\":\"a\\nb\\tc\\u0001\""))
        assertEquals("a\nb\tc\u0001", VaultMetaJson.decode(json).vaultId)
    }

    @Test
    fun `wrapped key entry carries role and wrapper from id`() {
        val wrapped = com.typedefai.cryptowl.crypto.WrappedKey(
            cipherText = byteArrayOf(1), nonce = byteArrayOf(2), authTag = byteArrayOf(3),
        )
        val entry = VaultMeta.WrappedKeyEntry.fromWrappedKey("kek:biokey", wrapped)

        assertEquals("kek", entry.role)
        assertEquals("biokey", entry.wrapper)
        assertEquals("AES-256-GCM", entry.algorithm)
        assertEquals(wrapped, entry.toWrappedKey())
    }

    @Test
    fun `canonical config matches the desktop v3 form`() {
        assertArrayEquals(
            """{"files":{"embed_max_bytes":524288},"name":"personal"}""".toByteArray(),
            VaultMetaJson.canonicalConfig("personal"),
        )
        assertEquals(
            524288L,
            VaultMetaJson.embedMaxBytesFromConfig(VaultMetaJson.canonicalConfig("personal")),
        )
        assertEquals(
            VaultMetaJson.DEFAULT_EMBED_MAX_BYTES,
            VaultMetaJson.embedMaxBytesFromConfig("""{"name":"x"}""".toByteArray()),
        )
    }

    /**
     * Pinned desktop v3 fixture (`cryptowl-desktop` `tst_vaultunlocker.cpp`
     * `makeMeta`): device secret 0x11…, password "correct horse battery
     * staple", argon2 salt 0x22…, hkdf salt 0x33…, VaultKey 0x44…, nonce
     * 0x55…, vault id "personal", timestamps 1780000000000.
     *
     * The expected canonical bytes and MAC were generated with the same
     * Qt-compatible canonicalization and are shared with the desktop repo —
     * changing either side breaks the other.
     */
    @Test
    fun `desktop v3 meta canonical bytes and mac vector`() {
        val smk = (
            "551afd2b0857e902118857a01a680b14e43ff2172950a71a5ec8ec147fcbc809" +
                "0471ff96c2b5486960b6596cfe6cfb7b412b93034a7950badda783f298b2e06f"
            ).hexToBytes()
        val fixture = VaultMeta(
            version = 3,
            vaultId = "personal",
            createdAt = 1780000000000,
            updatedAt = 1780000000000,
            kdf = VaultMeta.Kdf(algorithm = "argon2id", mKib = 19456, t = 2, p = 1),
            salts = VaultMeta.Salts(
                argon2 = ByteArray(32) { 0x22 },
                hkdf = ByteArray(32) { 0x33 },
                secondary = null,
            ),
            wrappedKeys = listOf(
                VaultMeta.WrappedKeyEntry(
                    id = "vault_key:smk",
                    role = "vault_key",
                    wrapper = "smk",
                    algorithm = "AES-256-GCM",
                    cipherText = "95a6a822d26244284a3b0f4b0f525d433dcd13ab523fe7da64b8dfb95849f045".hexToBytes(),
                    nonce = ByteArray(12) { 0x55 },
                    authTag = "f4e47f45e19f97f44e5feb86379dad10".hexToBytes(),
                ),
            ),
        )

        assertEquals(DESKTOP_V3_CANONICAL, VaultMetaJson.canonicalWithoutMac(fixture))
        val macKey = smk.copyOfRange(32, 64)
        val mac = HmacSha256.mac(macKey, DESKTOP_V3_CANONICAL.toByteArray(Charsets.UTF_8))
        assertEquals(
            "e88b2e9a41b6debd14d1160eb7603314d4979f6cd269459038703d063dbd9b28",
            mac.toHex(),
        )
        assertEquals(
            "X25JX-6J1PV-FBT56-H2R7B-ER1K2-KA9F7-VCT9M-MB41R-E0YGC-FDXKC-M0",
            CrockfordBase32.encode(mac),
        )
        assertEquals(CrockfordBase32.encode(mac), VaultMetaJson.computeMac(fixture, macKey))
    }

    // SMK from the fixed vaultlib vectors (test_vaultlib.py VECTOR_SMK)
    private val decodedSmk: ByteArray =
        ("551afd2b0857e902118857a01a680b14e43ff2172950a71a5ec8ec147fcbc809" +
            "0471ff96c2b5486960b6596cfe6cfb7b412b93034a7950badda783f298b2e06f").hexToBytes()

    private fun unwrapSmk(wrapped: com.typedefai.cryptowl.crypto.WrappedKey): ByteArray {
        val nonce = wrapped.nonce
        return com.typedefai.cryptowl.crypto.AesGcm.decrypt(
            key = decodedSmk.copyOfRange(0, 32),
            nonce = nonce,
            aad = "vault_key:smk".toByteArray(),
            encrypted = com.typedefai.cryptowl.crypto.AuthEncryptedData(wrapped.cipherText, wrapped.authTag),
        )
    }

    private fun String.hexToBytes(): ByteArray =
        ByteArray(length / 2) { Integer.parseInt(substring(it * 2, it * 2 + 2), 16).toByte() }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        /** Qt-canonical `vault.meta` minus `mac` for the pinned desktop fixture. */
        const val DESKTOP_V3_CANONICAL =
            "{\"created_at\":1780000000000," +
                "\"kdf\":{\"algorithm\":\"argon2id\",\"m_kib\":19456,\"p\":1,\"t\":2}," +
                "\"salts\":{\"argon2\":\"48H24-8H248-H248H-248H2-48H24-8H248-H248H-248H2-48H24-8H248-H0\"," +
                "\"hkdf\":\"6CSK6-CSK6C-SK6CS-K6CSK-6CSK6-CSK6C-SK6CS-K6CSK-6CSK6-CSK6C-SG\"}," +
                "\"updated_at\":1780000000000,\"vault_id\":\"personal\",\"version\":3," +
                "\"wrapped_keys\":[{\"algorithm\":\"AES-256-GCM\"," +
                "\"auth_tag\":\"YKJ7Y-HF1KY-BZ8KJ-ZXE33-F7DD2-0\"," +
                "\"ciphertext\":\"JPKAG-8PJC9-22GJH-V1X5G-YMJX8-CYWT4-XBA8Z-YFPK4-Q3FVJ-P29Y1-2G\"," +
                "\"id\":\"vault_key:smk\",\"nonce\":\"ANANA-NANAN-ANANA-NANAG\"," +
                "\"role\":\"vault_key\",\"wrapper\":\"smk\"}]}"
    }
}
