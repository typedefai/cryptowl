package com.typedefai.cryptowl

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.typedefai.cryptowl.R
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.vault.BioKeySetup
import com.typedefai.cryptowl.vault.UnlockService
import com.typedefai.cryptowl.vault.VaultBackup
import com.typedefai.cryptowl.vault.VaultCreator
import com.typedefai.cryptowl.vault.VaultMeta
import com.typedefai.cryptowl.vault.VaultSession
import com.typedefai.cryptowl.vault.VaultStore
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AppScreen {
    data object Loading : AppScreen
    data object Intro : AppScreen
    data object PasswordSetup : AppScreen
    data object BiometricSetup : AppScreen
    data object Home : AppScreen
    data object Unlock : AppScreen
    data object Restore : AppScreen
    data object Moments : AppScreen
    data object Chat : AppScreen
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private companion object {
        const val TAG = "MainViewModel"
        const val PATHS_TAG = "cwl:Paths"
        const val MODEL_DIR = "model"
    }

    private val _screen = MutableStateFlow<AppScreen>(AppScreen.Loading)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    private val _creatingVault = MutableStateFlow(false)
    val creatingVault: StateFlow<Boolean> = _creatingVault.asStateFlow()

    private val _vaultError = MutableStateFlow<String?>(null)
    val vaultError: StateFlow<String?> = _vaultError.asStateFlow()

    private val _biometricReady = MutableStateFlow(false)
    val biometricReady: StateFlow<Boolean> = _biometricReady.asStateFlow()

    private val _biometricCipher = MutableStateFlow<Cipher?>(null)
    val biometricCipher: StateFlow<Cipher?> = _biometricCipher.asStateFlow()

    private val _biometricError = MutableStateFlow<String?>(null)
    val biometricError: StateFlow<String?> = _biometricError.asStateFlow()

    private val _session = MutableStateFlow<VaultSession?>(null)
    val session: StateFlow<VaultSession?> = _session.asStateFlow()

    private val _unlocking = MutableStateFlow(false)
    val unlocking: StateFlow<Boolean> = _unlocking.asStateFlow()

    private val _unlockError = MutableStateFlow<String?>(null)
    val unlockError: StateFlow<String?> = _unlockError.asStateFlow()

    private val masterPassword = AtomicReference<ProtectedValue?>(null)
    private val preparedBiometric = AtomicReference<BioKeySetup.Prepared?>(null)

    private val vaultId = VaultStore.DEFAULT_VAULT_ID

    // --------------------------------------------------- restore / backup

    private val _restoreProgress = MutableStateFlow<VaultBackup.Progress?>(null)
    val restoreProgress: StateFlow<VaultBackup.Progress?> = _restoreProgress.asStateFlow()

    private val _restoreError = MutableStateFlow<String?>(null)
    val restoreError: StateFlow<String?> = _restoreError.asStateFlow()

    private val _backupProgress = MutableStateFlow<VaultBackup.Progress?>(null)
    val backupProgress: StateFlow<VaultBackup.Progress?> = _backupProgress.asStateFlow()

    private val _backupError = MutableStateFlow<String?>(null)
    val backupError: StateFlow<String?> = _backupError.asStateFlow()

    /** On-device LLM chat (LiteRT-LM). Owned here so the engine survives screen changes.
     *  Gallery behavior: the model initializes when the chat screen opens and
     *  is cleaned up when leaving it — not loaded at app start. */
    val chat = ChatViewModel(getApplication<Application>().applicationContext)

    init {
        logStoragePaths()
        val onboarded = VaultStore.isOnboarded(getApplication())
        _screen.value = if (onboarded) AppScreen.Home else AppScreen.Intro
    }

    /** One-shot dump of every storage location the app touches (tag: cwl:Paths). */
    private fun logStoragePaths() {
        val app = getApplication<Application>()
        val external = app.getExternalFilesDir(null)
        val vaultId = VaultStore.DEFAULT_VAULT_ID
        Log.d(
            PATHS_TAG,
            "storage paths:\n" +
                "internal filesDir      = ${app.filesDir}\n" +
                "internal cacheDir      = ${app.cacheDir}\n" +
                "external filesDir      = ${app.getExternalFilesDir(null)}\n" +
                "external cacheDir      = ${app.externalCacheDir}\n" +
                "vault dir              = ${VaultStore.vaultDir(app, vaultId)}\n" +
                "  vault.meta           = ${VaultStore.metaFile(app, vaultId)} (exists=${VaultStore.metaFile(app, vaultId).exists()})\n" +
                "  vault.db             = ${VaultStore.dbFile(app, vaultId)}\n" +
                "  config.json          = ${VaultStore.configFile(app, vaultId)}\n" +
                "  config.sig           = ${VaultStore.configSigFile(app, vaultId)}\n" +
                "  device_secret        = ${VaultStore.deviceSecretFile(app, vaultId)}\n" +
                "vault index            = ${VaultStore.indexFile(app)} (exists=${VaultStore.indexFile(app).exists()})\n" +
                "model dir (external)   = ${File(app.getExternalFilesDir(null), MODEL_DIR)}\n" +
                "model dir (internal)   = ${File(app.filesDir, MODEL_DIR)}\n" +
                "shared prefs (device secret) = ${app.filesDir.parentFile}/shared_prefs/cryptowl.vault.xml\n" +
                "shared prefs (chat settings) = ${app.filesDir.parentFile}/shared_prefs/cryptowl.chat.settings.xml",
        )
    }

    fun startOnboarding() {
        _screen.value = AppScreen.PasswordSetup
    }

    fun createVault(password: ProtectedValue) {
        Log.d(TAG, "createVault: start")
        _creatingVault.value = true
        _vaultError.value = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    VaultCreator(getApplication()).create(password)
                }
                Log.d(TAG, "createVault: success")
                masterPassword.set(password)
                _screen.value = AppScreen.BiometricSetup
            } catch (e: Throwable) {
                Log.e(TAG, "createVault failed", e)
                _vaultError.value = e.message ?: getApplication<Application>().getString(R.string.error_create_vault_failed)
            } finally {
                _creatingVault.value = false
            }
        }
    }

    /** Prepares the biometric wrap (derives VaultKey, creates BioKey encrypt cipher). */
    fun prepareBiometric() {
        val password = masterPassword.get() ?: run {
            _biometricError.value = getApplication<Application>().getString(R.string.error_biometric_password_lost)
            return
        }
        if (BiometricManager.from(getApplication()).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            _biometricError.value = getApplication<Application>().getString(R.string.error_biometric_not_enrolled)
            return
        }
        viewModelScope.launch {
            try {
                val prepared = withContext(Dispatchers.IO) {
                    BioKeySetup(getApplication()).prepare(password, vaultId)
                }
                preparedBiometric.getAndSet(prepared)?.let { BioKeySetup(getApplication()).cancel(it) }
                _biometricCipher.value = prepared.cipher
                _biometricReady.value = true
            } catch (e: Exception) {
                Log.e(TAG, "prepareBiometric failed", e)
                _biometricError.value = e.message ?: getApplication<Application>().getString(R.string.error_biometric_setup_failed)
            }
        }
    }

    /** Completes the wrap with the biometric-authorized cipher. */
    fun completeBiometric(cipher: Cipher) {
        val prepared = preparedBiometric.get() ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    BioKeySetup(getApplication()).complete(prepared, cipher)
                }
                finishOnboarding()
            } catch (e: Exception) {
                Log.e(TAG, "completeBiometric failed", e)
                _biometricError.value = e.message ?: getApplication<Application>().getString(R.string.error_biometric_setup_failed)
            } finally {
                preparedBiometric.set(null)
                _biometricCipher.value = null
                _biometricReady.value = false
            }
        }
    }

    /** Prompt was canceled/errored: wipe the prepared keys but stay on the screen. */
    fun cancelBiometricPrompt() {
        preparedBiometric.getAndSet(null)?.let { BioKeySetup(getApplication()).cancel(it) }
        _biometricCipher.value = null
        _biometricReady.value = false
    }

    /** User chose to skip fingerprint setup entirely. */
    fun skipBiometric() {
        cancelBiometricPrompt()
        finishOnboarding()
    }

    private fun finishOnboarding() {
        masterPassword.getAndSet(null)?.clear()
        _screen.value = AppScreen.Home
    }

    // ------------------------------------------------------------ vault unlock

    fun openVault() {
        _unlockError.value = null
        _screen.value = AppScreen.Unlock
    }

    fun unlockVault(password: ProtectedValue) {
        _unlocking.value = true
        _unlockError.value = null
        viewModelScope.launch {
            try {
                val session = withContext(Dispatchers.IO) {
                    UnlockService(getApplication()).unlock(password)
                }
                _session.value?.close()
                _session.value = session
                _screen.value = AppScreen.Moments
            } catch (e: Exception) {
                Log.e(TAG, "unlockVault failed", e)
                _unlockError.value = e.message ?: getApplication<Application>().getString(R.string.error_unlock_failed)
            } finally {
                _unlocking.value = false
            }
        }
    }

    fun lockVault() {
        _session.value?.close()
        _session.value = null
        _screen.value = AppScreen.Home
    }

    // --------------------------------------------------- restore / backup

    fun openRestore() {
        _restoreError.value = null
        _restoreProgress.value = null
        _screen.value = AppScreen.Restore
    }

    fun cancelRestore() {
        _screen.value = if (VaultStore.isOnboarded(getApplication(), vaultId)) AppScreen.Home else AppScreen.Intro
    }

    /** Restores the vault picked via SAF (a desktop-produced vault folder). */
    fun restoreVault(treeUri: Uri) {
        _restoreError.value = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    VaultBackup(getApplication()).restore(treeUri, vaultId) { _restoreProgress.value = it }
                }
                _screen.value = AppScreen.Unlock
            } catch (e: Exception) {
                Log.e(TAG, "restoreVault failed", e)
                _restoreError.value = e.message
                    ?: getApplication<Application>().getString(R.string.error_restore_failed)
            } finally {
                _restoreProgress.value = null
            }
        }
    }

    /** Copies the current vault into the SAF directory picked by the user. */
    fun backupVault(treeUri: Uri) {
        _backupError.value = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    VaultBackup(getApplication()).backup(treeUri, vaultId) { _backupProgress.value = it }
                }
            } catch (e: Exception) {
                Log.e(TAG, "backupVault failed", e)
                _backupError.value = e.message
                    ?: getApplication<Application>().getString(R.string.error_backup_failed)
            } finally {
                _backupProgress.value = null
            }
        }
    }

    // ------------------------------------------------------------ ai chat

    /** Chat is only reachable from an unlocked vault (Moments). */
    fun openChat() {
        _screen.value = AppScreen.Chat
    }

    fun closeChat() {
        _screen.value = AppScreen.Moments
    }

    override fun onCleared() {
        chat.close()
    }
}
