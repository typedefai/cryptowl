package com.typedefai.cryptowl.crypto

/**
 * Argon2 password hashing, backed by the official PHC reference
 * implementation (phc-winner-argon2) via JNI.
 *
 * Thread-safe: argon2_hash may use up to [parallelism] internal threads.
 */
object Argon2 {
    init {
        System.loadLibrary("cryptowl")
    }

    /** The three Argon2 variants, as defined by the reference implementation. */
    enum class Type(val id: Int) {
        ARGON2d(0),
        ARGON2i(1),
        ARGON2id(2),
    }

    /**
     * Hashes [password] with the given parameters and returns the raw hash bytes.
     *
     * @param mCost memory cost in KiB (e.g. 19456 for ~19 MiB)
     * @param tCost time cost (iterations)
     * @param parallelism number of threads (1..4)
     * @param type algorithm variant, defaults to [Type.ARGON2id]
     */
    fun hash(
        password: ByteArray,
        salt: ByteArray,
        mCost: Int,
        tCost: Int,
        parallelism: Int = 1,
        hashLen: Int = 32,
        type: Type = Type.ARGON2id,
    ): ByteArray = nativeHash(password, salt, mCost, tCost, parallelism, hashLen, type.id)

    /**
     * Hashes [password] and returns the PHC-encoded string
     * (e.g. `$argon2id$v=19$m=19456,t=2,p=1$...`).
     */
    fun hashEncoded(
        password: ByteArray,
        salt: ByteArray,
        mCost: Int,
        tCost: Int,
        parallelism: Int = 1,
        hashLen: Int = 32,
        type: Type = Type.ARGON2id,
    ): String = nativeHashEncoded(password, salt, mCost, tCost, parallelism, hashLen, type.id)

    /**
     * Verifies [password] against a PHC-encoded hash produced by
     * [hashEncoded]. Returns true if the encoded hash decodes and matches.
     */
    fun verify(
        encoded: String,
        password: ByteArray,
        type: Type = Type.ARGON2id,
    ): Boolean = nativeVerify(encoded, password, type.id)

    private external fun nativeHash(
        password: ByteArray,
        salt: ByteArray,
        mCost: Int,
        tCost: Int,
        parallelism: Int,
        hashLen: Int,
        type: Int,
    ): ByteArray

    private external fun nativeHashEncoded(
        password: ByteArray,
        salt: ByteArray,
        mCost: Int,
        tCost: Int,
        parallelism: Int,
        hashLen: Int,
        type: Int,
    ): String

    private external fun nativeVerify(encoded: String, password: ByteArray, type: Int): Boolean
}
