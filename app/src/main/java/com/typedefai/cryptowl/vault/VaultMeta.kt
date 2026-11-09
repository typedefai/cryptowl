package com.typedefai.cryptowl.vault

import com.typedefai.cryptowl.crypto.CrockfordBase32
import com.typedefai.cryptowl.crypto.HmacSha256
import com.typedefai.cryptowl.crypto.WrappedKey

/**
 * Parsed `vault.meta`: bootstrap material readable before the DB opens —
 * per-vault salts, KDF parameters and the wrapped VaultKey copies.
 * See docs/design.md "Vault Meta File (vault.meta)".
 */
data class VaultMeta(
    val version: Int,
    val vaultId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val kdf: Kdf,
    val salts: Salts,
    val wrappedKeys: List<WrappedKeyEntry>,
    val mac: Mac? = null,
) {
    data class Kdf(
        val algorithm: String,
        val mKib: Int,
        val t: Int,
        val p: Int,
    )

    data class Salts(
        val argon2: ByteArray,
        val hkdf: ByteArray,
        val secondary: ByteArray?,
    )

    data class Mac(
        val algorithm: String,
        val value: String,
    )

    data class WrappedKeyEntry(
        val id: String,
        val role: String,
        val wrapper: String,
        val algorithm: String,
        val cipherText: ByteArray,
        val nonce: ByteArray,
        val authTag: ByteArray,
    ) {
        fun toWrappedKey(): WrappedKey = WrappedKey(cipherText, nonce, authTag, algorithm)

        companion object {
            fun fromWrappedKey(id: String, wrapped: WrappedKey): WrappedKeyEntry =
                WrappedKeyEntry(
                    id = id,
                    role = id.substringBefore(':'),
                    wrapper = id.substringAfter(':'),
                    algorithm = wrapped.algorithm,
                    cipherText = wrapped.cipherText,
                    nonce = wrapped.nonce,
                    authTag = wrapped.authTag,
                )
        }
    }
}

/**
 * Canonical JSON for `vault.meta`, byte-exact with the C++/Qt desktop
 * reference (`cryptowl-desktop`): `QJsonDocument::toJson(Compact)` — compact
 * separators, lexicographic key order at every nesting level, binary fields
 * as Crockford Base32, and **raw UTF-8 for non-ASCII** (Qt does not emit
 * `\uXXXX` escapes). Control characters use the short escapes where JSON
 * defines them and `\u00xx` otherwise.
 *
 * The MAC (HMAC-SHA256 with the MAC Key) is computed over the canonical JSON
 * of the document excluding the `mac` field itself. Two documents encode to
 * the same canonical bytes iff their key-sorted trees are equal, so the MAC
 * is stable across writers.
 */
object VaultMetaJson {

    /** `files.embed_max_bytes` default (docs/items.md: 512 KiB). */
    const val DEFAULT_EMBED_MAX_BYTES = 512L * 1024

    fun encode(meta: VaultMeta): String = write(metaToJson(meta))

    /** Canonical JSON of everything except the `mac` field (for MAC computation). */
    fun canonicalWithoutMac(meta: VaultMeta): String = write(metaToJson(meta.copy(mac = null)))

    /**
     * Canonical `config.json`:
     * `{"files":{"embed_max_bytes":N},"name":"<vaultId>"}` — byte-exact with
     * the desktop `VaultCreator` (Qt sorted keys: `files` before `name`).
     */
    fun canonicalConfig(
        vaultId: String,
        embedMaxBytes: Long = DEFAULT_EMBED_MAX_BYTES,
    ): ByteArray = write(
        mapOf(
            "files" to mapOf("embed_max_bytes" to embedMaxBytes),
            "name" to vaultId,
        ),
    ).toByteArray(Charsets.UTF_8)

    /** Reads `files.embed_max_bytes` from a `config.json` blob (1 KiB..64 MiB). */
    fun embedMaxBytesFromConfig(configJson: ByteArray): Long {
        val root = try {
            Parser(configJson.toString(Charsets.UTF_8)).parseObject()
        } catch (e: Exception) {
            return DEFAULT_EMBED_MAX_BYTES
        }
        val files = root["files"] as? Map<*, *> ?: return DEFAULT_EMBED_MAX_BYTES
        val value = when (val raw = files["embed_max_bytes"]) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull()
            else -> null
        } ?: return DEFAULT_EMBED_MAX_BYTES
        return if (value <= 0 || value > 64L * 1024 * 1024) DEFAULT_EMBED_MAX_BYTES else value
    }

    /** HMAC-SHA256 over the canonical JSON, Crockford Base32 encoded. */
    fun computeMac(meta: VaultMeta, macKey: ByteArray): String =
        CrockfordBase32.encode(HmacSha256.mac(macKey, canonicalWithoutMac(meta).toByteArray(Charsets.UTF_8)))

    /** Parses [json], returning the meta with `mac` populated when present. */
    fun decode(json: String): VaultMeta {
        val root = Parser(json).parseObject()
        fun str(key: String): String = root[key] as? String ?: error("missing '$key'")
        fun num(key: String): Long = when (val v = root[key]) {
            is String -> v.toLong()
            is Number -> v.toLong()
            else -> error("missing '$key'")
        }
        val kdf = root["kdf"] as Map<String, Any>
        val salts = root["salts"] as Map<String, Any>
        val wrappedKeys = (root["wrapped_keys"] as? List<*>)?.map { e ->
            val entry = e as Map<String, Any>
            VaultMeta.WrappedKeyEntry(
                id = entry["id"] as String,
                role = entry["role"] as String,
                wrapper = entry["wrapper"] as String,
                algorithm = entry["algorithm"] as String,
                cipherText = CrockfordBase32.decode(entry["ciphertext"] as String),
                nonce = CrockfordBase32.decode(entry["nonce"] as String),
                authTag = CrockfordBase32.decode(entry["auth_tag"] as String),
            )
        } ?: emptyList()
        val mac = (root["mac"] as? Map<*, *>)?.let { m ->
            VaultMeta.Mac(
                algorithm = m["algorithm"] as String,
                value = m["value"] as String,
            )
        }
        return VaultMeta(
            version = num("version").toInt(),
            vaultId = str("vault_id"),
            createdAt = num("created_at"),
            updatedAt = num("updated_at"),
            kdf = VaultMeta.Kdf(
                algorithm = kdf["algorithm"] as String,
                mKib = intOf(kdf["m_kib"]),
                t = intOf(kdf["t"]),
                p = intOf(kdf["p"]),
            ),
            salts = VaultMeta.Salts(
                argon2 = CrockfordBase32.decode(salts["argon2"] as String),
                hkdf = CrockfordBase32.decode(salts["hkdf"] as String),
                secondary = (salts["secondary"] as? String)?.let(CrockfordBase32::decode),
            ),
            wrappedKeys = wrappedKeys,
            mac = mac,
        )
    }

    // ------------------------------------------------------------------
    // Canonical writer: sorted keys at every level, python-compatible.
    // ------------------------------------------------------------------

    private fun metaToJson(meta: VaultMeta): Map<String, Any?> {
        val salts = LinkedHashMap<String, Any?>()
        salts["argon2"] = CrockfordBase32.encode(meta.salts.argon2)
        salts["hkdf"] = CrockfordBase32.encode(meta.salts.hkdf)
        meta.salts.secondary?.let { salts["secondary"] = CrockfordBase32.encode(it) }

        val wrappedKeys = meta.wrappedKeys.map { entry ->
            mapOf(
                "id" to entry.id,
                "role" to entry.role,
                "wrapper" to entry.wrapper,
                "algorithm" to entry.algorithm,
                "ciphertext" to CrockfordBase32.encode(entry.cipherText),
                "nonce" to CrockfordBase32.encode(entry.nonce),
                "auth_tag" to CrockfordBase32.encode(entry.authTag),
            )
        }

        val result = LinkedHashMap<String, Any?>()
        result["version"] = meta.version
        result["vault_id"] = meta.vaultId
        result["created_at"] = meta.createdAt
        result["updated_at"] = meta.updatedAt
        result["kdf"] = mapOf(
            "algorithm" to meta.kdf.algorithm,
            "m_kib" to meta.kdf.mKib,
            "t" to meta.kdf.t,
            "p" to meta.kdf.p,
        )
        result["salts"] = salts
        result["wrapped_keys"] = wrappedKeys
        meta.mac?.let { result["mac"] = mapOf("algorithm" to it.algorithm, "value" to it.value) }
        return result
    }

    /** Writes [obj] as compact JSON with lexicographically sorted object keys. */
    private fun write(obj: Any?): String {
        val sb = StringBuilder()
        writeValue(sb, obj)
        return sb.toString()
    }

    private fun writeValue(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is Boolean -> sb.append(if (value) "true" else "false")
            is Int, is Long -> sb.append(value.toString())
            is String -> writeString(sb, value)
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for (key in value.keys.sortedBy { it.toString() }) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, key.toString())
                    sb.append(':')
                    writeValue(sb, value[key])
                }
                sb.append('}')
            }
            is List<*> -> {
                sb.append('[')
                for ((i, item) in value.withIndex()) {
                    if (i > 0) sb.append(',')
                    writeValue(sb, item)
                }
                sb.append(']')
            }
            else -> error("unsupported JSON value: $value")
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> {
                    // Qt (QJsonDocument::toJson) emits raw UTF-8 and only
                    // escapes control characters — never \u-escaping
                    // non-ASCII. Desktop vaults with non-ASCII vault ids
                    // depend on this for MAC verification.
                    if (c.code < 0x20) {
                        sb.append("\\u").append(String.format("%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        sb.append('"')
    }

    private fun intOf(value: Any?): Int = when (value) {
        is String -> value.toInt()
        is Number -> value.toInt()
        else -> error("expected number, got $value")
    }

    /** Minimal JSON parser: objects, arrays, strings, numbers, booleans, null. */
    private class Parser(private val input: String) {
        private var pos = 0

        fun parseObject(): Map<String, Any> {
            expect('{')
            val result = LinkedHashMap<String, Any>()
            skipWs()
            if (peek() == '}') { pos++; return result }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                expect(':')
                skipWs()
                result[key] = parseValue()
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    '}' -> { pos++; return result }
                    else -> error("unexpected '${peek()}' at $pos")
                }
            }
        }

        private fun parseValue(): Any {
            return when (peek()) {
                '{' -> parseObject()
                '[' -> {
                    pos++
                    val list = mutableListOf<Any>()
                    skipWs()
                    if (peek() == ']') { pos++; return list }
                    while (true) {
                        skipWs()
                        list.add(parseValue())
                        skipWs()
                        when (peek()) {
                            ',' -> pos++
                            ']' -> { pos++; return list }
                            else -> error("unexpected '${peek()}' at $pos")
                        }
                    }
                }
                '"' -> parseString()
                't' -> { expectWord("true"); true }
                'f' -> { expectWord("false"); false }
                'n' -> { expectWord("null"); "null" }
                else -> parseNumber()
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < input.length) {
                val c = input[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        val esc = input[pos++]
                        when (esc) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                val hex = input.substring(pos, pos + 4)
                                pos += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> error("invalid escape '\\$esc'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
            error("unterminated string")
        }

        private fun parseNumber(): Number {
            val start = pos
            while (pos < input.length && (input[pos].isDigit() || input[pos] in ".-+eE")) pos++
            return input.substring(start, pos).toLongOrNull() ?: input.substring(start, pos).toDouble()
        }

        private fun expectWord(word: String) {
            if (pos + word.length <= input.length && input.substring(pos, pos + word.length) == word) pos += word.length
            else error("expected '$word' at $pos")
        }

        private fun expect(c: Char) {
            if (pos >= input.length || input[pos] != c) error("expected '$c' at $pos")
            pos++
        }

        private fun peek(): Char = if (pos < input.length) input[pos] else '\u0000'

        private fun skipWs() {
            while (pos < input.length && input[pos].isWhitespace()) pos++
        }
    }
}
