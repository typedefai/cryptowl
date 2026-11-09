package com.typedefai.cryptowl.vault

import android.app.Application
import com.typedefai.cryptowl.crypto.KdfParams
import com.typedefai.cryptowl.crypto.KdfService
import com.typedefai.cryptowl.crypto.ProtectedValue
import javax.crypto.Cipher

/**
 * Secret-tier key acquisition (docs/design.md L2) shared by the editor and
 * history screens.
 *
 * Android binds the KEK to the Keystore BioKey (`kek:biokey`), so a normal
 * access is one BiometricPrompt. Two fallbacks exist:
 * - no KEK yet → a fresh KEK is generated and wrapped by the authorized BioKey;
 * - imported desktop vault with `kek:smk` → the master password is re-entered
 *   once, the SMK unwraps the desktop KEK, and it is re-wrapped under the
 *   BioKey (`kek:biokey`), consuming the stale row.
 */
class SecretAccess(
    private val app: Application,
    private val session: VaultSession,
    private val kdf: KdfService = KdfService(),
) {

    enum class KekMode { UNLOCK, CREATE, IMPORT }

    data class KekPreparation(val mode: KekMode, val cipher: Cipher)

    fun prepareKek(): KekPreparation {
        val service = KekService(session.db, kdf)
        return when {
            service.exists() -> KekPreparation(KekMode.UNLOCK, service.prepareUnlockCipher())
            service.hasDesktopKek() -> KekPreparation(KekMode.IMPORT, service.prepareCreateCipher())
            else -> KekPreparation(KekMode.CREATE, service.prepareCreateCipher())
        }
    }

    fun completeKek(
        preparation: KekPreparation,
        authorizedCipher: Cipher,
        masterPassword: ProtectedValue? = null,
    ): ProtectedValue {
        val service = KekService(session.db, kdf)
        return when (preparation.mode) {
            KekMode.UNLOCK -> service.unlock(authorizedCipher)
            KekMode.CREATE -> service.completeCreate(authorizedCipher)
            KekMode.IMPORT -> {
                val password = masterPassword
                    ?: throw VaultLockedException("master password required to import the Secret key")
                val smk = deriveSmk(password)
                try {
                    service.importDesktopKek(smk, authorizedCipher)
                        ?: throw VaultLockedException("the desktop KEK binding is missing")
                } finally {
                    smk.clear()
                }
            }
        }
    }

    /** Derives SMK from a freshly entered master password (for the import path). */
    fun deriveSmk(masterPassword: ProtectedValue): ProtectedValue {
        val metaFile = VaultStore.metaFile(app, session.vaultId)
        val meta = VaultMetaJson.decode(metaFile.readText())
        if (meta.version != 3) throw VaultOpenException("unsupported vault format version ${meta.version}")
        val params = KdfParams(
            algorithm = meta.kdf.algorithm,
            mCostKiB = meta.kdf.mKib,
            tCost = meta.kdf.t,
            parallelism = meta.kdf.p,
        )
        val deviceSecret = DeviceSecretStore.getOrCreate(app)
        val tmk = kdf.createTransformedMasterKey(masterPassword, deviceSecret, meta.salts.argon2, params)
        try {
            return kdf.createStretchedMasterKey(tmk, meta.vaultId.toByteArray(Charsets.UTF_8), meta.salts.hkdf)
        } finally {
            tmk.clear()
            deviceSecret.clear()
        }
    }
}
