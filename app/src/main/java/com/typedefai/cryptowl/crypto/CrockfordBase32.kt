package com.typedefai.cryptowl.crypto

/**
 * Crockford Base32 (RFC 4648 alphabet variant): "0123456789ABCDEFGHJKMNPQRSTVWXYZ".
 * Encoding groups output in blocks of 5 characters separated by hyphens; decoding
 * ignores hyphens and case and accepts the aliases I/L -> 1, O -> 0.
 */
object CrockfordBase32 {

    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun encode(data: ByteArray): String {
        val builder = StringBuilder()
        var buffer = 0
        var bits = 0
        var groupCount = 0
        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                appendChar(builder, (buffer shr bits) and 0x1F, groupCount)
                groupCount++
            }
        }
        if (bits > 0) {
            appendChar(builder, (buffer shl (5 - bits)) and 0x1F, groupCount)
        }
        return builder.toString()
    }

    fun decode(encoded: String): ByteArray {
        val output = ByteArray((encoded.length * 5) / 8)
        var buffer = 0
        var bits = 0
        var position = 0
        for (c in encoded) {
            if (c == '-') continue
            val value = charValue(c)
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                bits -= 8
                output[position++] = ((buffer shr bits) and 0xFF).toByte()
            }
        }
        return output.copyOf(position)
    }

    private fun appendChar(builder: StringBuilder, index: Int, groupCount: Int) {
        if (groupCount > 0 && groupCount % 5 == 0) {
            builder.append('-')
        }
        builder.append(ALPHABET[index])
    }

    private fun charValue(c: Char): Int {
        val normalized = when (val upper = c.uppercaseChar()) {
            'I', 'L' -> '1'
            'O' -> '0'
            else -> upper
        }
        val index = ALPHABET.indexOf(normalized)
        if (index < 0) {
            throw IllegalArgumentException("Invalid Crockford Base32 character: $c")
        }
        return index
    }
}
