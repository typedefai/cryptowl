package com.typedefai.cryptowl.crypto

import android.os.SystemClock

/**
 * Argon2 password hashing, injectable so JVM unit tests can stub it
 * (the JNI binding cannot run outside a device/emulator).
 */
fun interface Argon2Hasher {
    fun hash(password: ByteArray, salt: ByteArray, params: KdfParams, hashLen: Int): ByteArray
}

/**
 * The key-derivation chain from docs/design.md (byte-exact with the desktop
 * reference `wechat_sns_export/vaultlib` — the cross-verification oracle):
 *
 *   P    = HMAC-SHA256(key=DeviceSecret, msg=MasterPassword)
 *   TMK  = Argon2id(P, salt=argon2Salt)
 *   SMK  = HKDF-SHA256(ikm=TMK, salt=hkdfSalt, info=vaultId, L=64)
 *   key  = SMK[0:32]  (unwrap VaultKey / SQLCipher key)
 *   mac  = SMK[32:64] (HMAC key for config integrity)
 *   KEK  = Argon2id(SecondaryPassword, secondarySalt)   (TS-KEK; wraps TopSecretKEK)
 *   FEK  = HKDF-SHA256(ikm=VaultKey, salt="", info="file", L=32)  (C-tier files)
 *
 * Keys are returned as [ProtectedValue] and never survive as heap byte arrays.
 * Envelope operations ([wrapKey]/[unwrapKey]) bind the wrapped-key id as AAD.
 */
class KdfService(
    private val hasher: Argon2Hasher = Argon2Hasher { password, salt, params, hashLen ->
        Argon2.hash(
            password = password,
            salt = salt,
            mCost = params.mCostKiB,
            tCost = params.tCost,
            parallelism = params.parallelism,
            hashLen = hashLen,
        )
    },
) {

    /**
     * Derives the 32-byte Transformed Master Key:
     * Argon2id(HMAC-SHA256(key=DeviceSecret, msg=MasterPassword), salt).
     */
    fun createTransformedMasterKey(
        masterPassword: ProtectedValue,
        deviceSecret: ProtectedValue,
        salt: ByteArray,
        params: KdfParams = KdfParams.OWASP,
    ): ProtectedValue = masterPassword.use { password ->
        deviceSecret.use { secret ->
            val preHashed = HmacSha256.mac(key = secret, message = password)
            try {
                val t0 = SystemClock.elapsedRealtime()
                val tmk = hasher.hash(preHashed, salt, params, KEY_SIZE)
                CryptoLog.d(
                    C,
                    "TMK: argon2id m=${params.mCostKiB}KiB t=${params.tCost} p=${params.parallelism} " +
                        "hashLen=$KEY_SIZE salt=${salt.toHexString(8)} took=${SystemClock.elapsedRealtime() - t0}ms " +
                        "tmk(${CryptoLog.key(tmk)})",
                )
                ProtectedValue.fromBinary(tmk)
            } finally {
                preHashed.fill(0)
            }
        }
    }

    /**
     * Stretches the 32-byte TMK into the 64-byte Stretched Master Key:
     * HKDF-SHA256(ikm=TMK, salt=hkdfSalt, info=vaultId, L=64).
     */
    fun createStretchedMasterKey(
        transformedMasterKey: ProtectedValue,
        vaultId: ByteArray,
        salt: ByteArray,
    ): ProtectedValue = transformedMasterKey.use { tmk ->
        ProtectedValue.fromBinary(
            Hkdf.deriveKey(ikm = tmk, salt = salt, info = vaultId, outputLength = SMK_SIZE),
        ).also {
            CryptoLog.d(
                C,
                "SMK: hkdf salt=${salt.toHexString(8)} info=${vaultId.decodeToString()} L=$SMK_SIZE " +
                    "tmk(${CryptoLog.key(tmk)}) -> smk(${CryptoLog.key(it)})",
            )
        }
    }

    /** SMK[0:32] — unwraps the VaultKey (SQLCipher key). */
    fun vaultKey(stretchedMasterKey: ProtectedValue): ProtectedValue =
        stretchedMasterKey.use { ProtectedValue.fromBinary(it.copyOfRange(0, KEY_SIZE)) }

    /** SMK[32:64] — HMAC-SHA256 key for config integrity. */
    fun macKey(stretchedMasterKey: ProtectedValue): ProtectedValue =
        stretchedMasterKey.use { ProtectedValue.fromBinary(it.copyOfRange(KEY_SIZE, SMK_SIZE)) }

    /**
     * Derives the Top-Secret KEK (TS-KEK) from the secondary password:
     * Argon2id(SecondaryPassword, secondarySalt).
     */
    fun createSecondaryKey(
        secondaryPassword: ProtectedValue,
        salt: ByteArray,
        params: KdfParams = KdfParams.OWASP,
    ): ProtectedValue = secondaryPassword.use { password ->
        val t0 = SystemClock.elapsedRealtime()
        ProtectedValue.fromBinary(hasher.hash(password, salt, params, KEY_SIZE)).also {
            CryptoLog.d(
                C,
                "TS-KEK: argon2id secondary password salt=${salt.toHexString(8)} " +
                    "took=${SystemClock.elapsedRealtime() - t0}ms tsKek(${CryptoLog.key(it)})",
            )
        }
    }

    /**
     * Wraps [key] with [wrappingKey] (AES-256-GCM, AAD = wrapped-key id).
     * A fresh nonce is generated when none is given.
     */
    fun wrapKey(
        key: ProtectedValue,
        wrappingKey: ProtectedValue,
        aad: ByteArray,
        nonce: ByteArray = RandomUtil.generateSecureBytes(AesGcm.NONCE_SIZE),
    ): WrappedKey = wrappingKey.use { wk ->
        key.use { k ->
            val encrypted = AesGcm.encrypt(wk, nonce, aad, k)
            CryptoLog.d(
                C,
                "wrapKey: aad='${aad.decodeToString()}' nonce=${nonce.toHexString()} " +
                    "KEK(${CryptoLog.key(wk)}) + key(${CryptoLog.key(k)}) -> " +
                    "cipher=${encrypted.cipherText.size}B tag=${encrypted.authTag.size}B",
            )
            WrappedKey(cipherText = encrypted.cipherText, nonce = nonce, authTag = encrypted.authTag)
        }
    }

    /**
     * Unwraps [wrapped] with [wrappingKey]; throws [javax.crypto.AEADBadTagException]
     * on wrong key, nonce, AAD or tampered ciphertext.
     */
    fun unwrapKey(wrapped: WrappedKey, wrappingKey: ProtectedValue, aad: ByteArray): ProtectedValue =
        wrappingKey.use { wk ->
            val plain = try {
                AesGcm.decrypt(
                    key = wk,
                    nonce = wrapped.nonce,
                    aad = aad,
                    encrypted = AuthEncryptedData(wrapped.cipherText, wrapped.authTag),
                )
            } catch (e: Throwable) {
                CryptoLog.e(
                    C,
                    "unwrapKey FAILED: aad='${aad.decodeToString()}' nonce=${wrapped.nonce.toHexString()} " +
                        "KEK(${CryptoLog.key(wk)}) cipher=${wrapped.cipherText.size}B",
                    e,
                )
                throw e
            }
            CryptoLog.d(
                C,
                "unwrapKey: aad='${aad.decodeToString()}' nonce=${wrapped.nonce.toHexString()} " +
                    "KEK(${CryptoLog.key(wk)}) -> key(${CryptoLog.key(plain)})",
            )
            ProtectedValue.fromBinary(plain)
        }

    /**
     * FEK — C-tier file encryption key:
     * HKDF-SHA256(ikm=VaultKey, salt="", info="file", L=32).
     */
    fun fileKey(vaultKey: ProtectedValue): ProtectedValue = vaultKey.use { vk ->
        CryptoLog.d(C, "FEK: hkdf info=file vaultKey(${CryptoLog.key(vk)})")
        ProtectedValue.fromBinary(
            Hkdf.deriveKey(ikm = vk, salt = ByteArray(0), info = "file".toByteArray(), outputLength = KEY_SIZE),
        ).also { CryptoLog.d(C, "FEK: derived fek(${CryptoLog.key(it)})") }
    }

    private companion object {
        const val C = "KdfService"
        const val KEY_SIZE = 32
        const val SMK_SIZE = 64
    }
}
