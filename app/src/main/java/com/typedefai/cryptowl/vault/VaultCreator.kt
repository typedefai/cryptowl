package com.typedefai.cryptowl.vault

import android.content.Context
import android.util.Log
import com.typedefai.cryptowl.crypto.KdfParams
import com.typedefai.cryptowl.crypto.KdfService
import com.typedefai.cryptowl.crypto.toHexString
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.crypto.RandomUtil
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

/**
 * Vault creation pipeline — see docs/design.md "Vault Meta File" and
 * "Vault Storage Layout".
 *
 * Order matters: vault.meta (with `vault_key:smk`) is written *before* the
 * DB is created, because the SQLCipher key comes from unwrapping it.
 * All intermediate keys are wiped on exit; only the wrapped copy and the
 * (ciphertext-only) schema data are persisted.
 */
class VaultCreator(
    private val context: Context,
    private val kdf: KdfService = KdfService(),
) {

    fun create(masterPassword: ProtectedValue, vaultId: String = VaultStore.DEFAULT_VAULT_ID): VaultMeta {
        val vaultDir = VaultStore.vaultDir(context, vaultId)
        if (VaultStore.metaFile(context, vaultId).exists()) {
            if (VaultStore.isOnboarded(context, vaultId)) {
                error("vault already exists: $vaultId")
            }
            // Partial vault from a crashed previous attempt (meta written but
            // db incomplete): wipe and start over.
            Log.w(TAG, "create: removing partial vault dir from a failed attempt")
            vaultDir.deleteRecursively()
        }
        vaultDir.mkdirs()

        Log.d(
            TAG,
            "create: vault layout — dir=$vaultDir\n" +
                "  meta=${VaultStore.metaFile(context, vaultId)}\n" +
                "  config=${VaultStore.configFile(context, vaultId)}\n" +
                "  configSig=${VaultStore.configSigFile(context, vaultId)}\n" +
                "  db=${VaultStore.dbFile(context, vaultId)}\n" +
                "  index=${VaultStore.indexFile(context)}\n" +
                "  deviceSecret=${VaultStore.deviceSecretFile(context, vaultId)}",
        )
        val deviceSecret = DeviceSecretStore.getOrCreate(context)
        Log.d(TAG, "create: device secret ready (${deviceSecret.binaryValue().size} bytes)")
        val argon2Salt = RandomUtil.generateSecureBytes(SALT_SIZE)
        val hkdfSalt = RandomUtil.generateSecureBytes(SALT_SIZE)
        val secondarySalt = RandomUtil.generateSecureBytes(SALT_SIZE)
        Log.d(
            TAG,
            "create: salts argon2=${argon2Salt.toHexString(8)} hkdf=${hkdfSalt.toHexString(8)} " +
                "secondary=${secondarySalt.toHexString(8)}",
        )

        val tmk = kdf.createTransformedMasterKey(masterPassword, deviceSecret, argon2Salt)
        Log.d(TAG, "create: TMK derived")
        val smk = kdf.createStretchedMasterKey(tmk, vaultId.toByteArray(Charsets.UTF_8), hkdfSalt)
        Log.d(TAG, "create: SMK derived")
        val vaultKey = ProtectedValue.fromBinary(RandomUtil.generateSecureBytes(KEY_SIZE))
        val wrappedVaultKey = kdf.wrapKey(
            key = vaultKey,
            wrappingKey = kdf.vaultKey(smk),
            aad = WRAPPED_VAULT_KEY_SMK.toByteArray(Charsets.UTF_8),
        )
        Log.d(TAG, "create: vault key wrapped")

        val now = System.currentTimeMillis()
        val meta = VaultMeta(
            version = META_VERSION,
            vaultId = vaultId,
            createdAt = now,
            updatedAt = now,
            kdf = VaultMeta.Kdf(
                algorithm = KdfParams.OWASP.algorithm,
                mKib = KdfParams.OWASP.mCostKiB,
                t = KdfParams.OWASP.tCost,
                p = KdfParams.OWASP.parallelism,
            ),
            // copies: the caller may re-derive from meta after the locals are wiped
            salts = VaultMeta.Salts(
                argon2 = argon2Salt.copyOf(),
                hkdf = hkdfSalt.copyOf(),
                secondary = secondarySalt.copyOf(),
            ),
            wrappedKeys = listOf(VaultMeta.WrappedKeyEntry.fromWrappedKey(WRAPPED_VAULT_KEY_SMK, wrappedVaultKey)),
        )
        val macKey = kdf.macKey(smk)
        val macValue = VaultMetaJson.computeMac(meta, macKey.binaryValue())
        val metaWithMac = meta.copy(mac = VaultMeta.Mac(algorithm = "HMAC-SHA256", value = macValue))

        try {
            writeConfig(macKey, vaultId)
            Log.d(
                TAG,
                "create: config written (${VaultStore.configFile(context, vaultId).length()}B), " +
                    "content=${VaultMetaJson.canonicalConfig(vaultId).decodeToString()}",
            )
            writeMetaAtomically(metaWithMac)
            Log.d(
                TAG,
                "create: vault.meta written (${VaultStore.metaFile(context, vaultId).length()}B)\n" +
                    VaultMetaJson.encode(metaWithMac),
            )
            createDatabase(vaultKey, vaultId)
            Log.d(TAG, "create: database created (${VaultStore.dbFile(context, vaultId).length()}B)")
            VaultStore.writeIndex(context, vaultId)
            Log.d(TAG, "create: index written (${VaultStore.indexFile(context).readText()})")
        } finally {
            tmk.clear()
            smk.clear()
            vaultKey.clear()
            macKey.clear()
            deviceSecret.clear()
            argon2Salt.fill(0)
            hkdfSalt.fill(0)
            secondarySalt.fill(0)
        }
        return metaWithMac
    }

    private fun writeConfig(macKey: ProtectedValue, vaultId: String) {
        val configBytes = VaultMetaJson.canonicalConfig(vaultId)
        val macKeyBytes = macKey.binaryValue()
        try {
            val config = VaultStore.configFile(context, vaultId)
            val tmpConfig = File(config.parentFile, "config.json.tmp")
            tmpConfig.writeBytes(configBytes)
            if (!tmpConfig.renameTo(config)) {
                tmpConfig.delete()
                throw IllegalStateException("failed to write config.json atomically")
            }
            val sig = com.typedefai.cryptowl.crypto.CrockfordBase32.encode(
                com.typedefai.cryptowl.crypto.HmacSha256.mac(macKeyBytes, configBytes),
            )
            val sigFile = VaultStore.configSigFile(context, vaultId)
            val tmpSig = File(sigFile.parentFile, "config.sig.tmp")
            tmpSig.writeText(sig)
            if (!tmpSig.renameTo(sigFile)) {
                tmpSig.delete()
                throw IllegalStateException("failed to write config.sig atomically")
            }
        } finally {
            macKeyBytes.fill(0)
        }
    }

    private fun writeMetaAtomically(meta: VaultMeta) {
        val metaFile = VaultStore.metaFile(context, meta.vaultId)
        val tmp = File(metaFile.parentFile, "vault.meta.tmp")
        tmp.writeText(VaultMetaJson.encode(meta))
        if (!tmp.renameTo(metaFile)) {
            tmp.delete()
            throw IllegalStateException("failed to write vault.meta atomically")
        }
    }

    private fun createDatabase(vaultKey: ProtectedValue, vaultId: String) {
        System.loadLibrary("sqlcipher")
        vaultKey.use { _ ->
            val db = SQLiteDatabase.openOrCreateDatabase(
                VaultStore.dbFile(context, vaultId), vaultKey.asSqlCipherRawKey(), null, null,
            )
            try {
                SchemaApplier.migrate(db, context)
            } finally {
                db.close()
            }
        }
    }

    private companion object {
        const val TAG = "cwl:VaultCreator"
        const val META_VERSION = 2
        const val KEY_SIZE = 32
        const val SALT_SIZE = 32
        const val WRAPPED_VAULT_KEY_SMK = "vault_key:smk"
    }
}
