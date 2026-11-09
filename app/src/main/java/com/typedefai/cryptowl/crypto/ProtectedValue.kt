package com.typedefai.cryptowl.crypto

import java.lang.ref.Cleaner
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Wrapper for sensitive byte data (passwords, keys).
 *
 * Backed by an off-heap direct [ByteBuffer]: the canonical copy is never moved
 * or duplicated by GC compaction. Scrub explicitly with [clear], or use [use]
 * for auto-cleared scoped access. A [Cleaner] backstop scrubs the buffer if the
 * value is dropped without [clear].
 *
 * Note: [binaryValue] returns a plain heap copy that the **caller** must clear.
 * Avoid storing secrets as [String]; [fromString] is for input boundaries only.
 */
class ProtectedValue private constructor(private val buffer: ByteBuffer) {

    @Synchronized
    fun binaryValue(): ByteArray = copyOf()

    @Synchronized
    fun <T> use(block: (ByteArray) -> T): T {
        val bytes = copyOf()
        try {
            return block(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    @Synchronized
    fun getText(): String = use { CrockfordBase32.encode(it) }

    @Synchronized
    fun clear() {
        buffer.rewind()
        while (buffer.hasRemaining()) {
            buffer.put(0)
        }
    }

    override fun toString(): String = "[ProtectedValue]"

    private fun copyOf(): ByteArray {
        val bytes = ByteArray(buffer.capacity())
        buffer.rewind()
        buffer.get(bytes)
        return bytes
    }

    companion object {
        private val cleaner = Cleaner.create()

        fun fromString(value: String): ProtectedValue {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            try {
                return fromBinary(bytes)
            } finally {
                bytes.fill(0)
            }
        }

        fun fromBinary(value: ByteArray): ProtectedValue {
            val buffer = ByteBuffer.allocateDirect(value.size)
            buffer.put(value)
            val protectedValue = ProtectedValue(buffer)
            cleaner.register(protectedValue, ScrubAction(buffer))
            return protectedValue
        }
    }

    private class ScrubAction(private val buffer: ByteBuffer) : Runnable {
        override fun run() {
            buffer.rewind()
            while (buffer.hasRemaining()) {
                buffer.put(0)
            }
        }
    }
}
