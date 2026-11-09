package com.typedefai.cryptowl

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.typedefai.cryptowl.R
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.settings.AutoLockPolicy
import com.typedefai.cryptowl.settings.SettingsStore
import com.typedefai.cryptowl.vault.BioKeyManager
import com.typedefai.cryptowl.vault.BioKeySetup
import com.typedefai.cryptowl.vault.MasterPasswordChange
import com.typedefai.cryptowl.vault.UnlockService
import com.typedefai.cryptowl.vault.VaultBackup
import com.typedefai.cryptowl.vault.VaultCreator
import com.typedefai.cryptowl.vault.VaultMeta
import com.typedefai.cryptowl.vault.VaultMetaJson
import com.typedefai.cryptowl.vault.VaultSession
import com.typedefai.cryptowl.vault.VaultStore
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Top-level app state. Navigation shells key off this instead of individual
 * screens: either onboarding is in progress, the vault is locked (full-screen
 * lock, biometric auto-prompt), or a vault is unlocked and the main shell
 * (bottom navigation) is shown.
 */
enum class AppState { ONBOARDING, LOCKED, UNLOCKED }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private companion object {
        const val TAG = "cwl:MainViewModel"
        const val PATHS_TAG = "cwl:Paths"
        const val MODEL_DIR = "model"
    }

    private val _appState = MutableStateFlow(AppState.LOCKED)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _creatingVault = MutableStateFlow(false)
    val creatingVault: StateFlow<Boolean> = _creatingVault.asStateFlow()

    /** Emits once each time vault creation succeeds (drives the setup wizard). */
    private val _vaultCreated = MutableStateFlow(false)
    val vaultCreated: StateFlow<Boolean> = _vaultCreated.asStateFlow()

    /** Emits when the optional biometric step is finished or skipped. */
    private val _onboardingDone = MutableStateFlow(false)
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

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

    /** True when the vault carries a `vault_key:biokey` copy + Keystore BioKey. */
    private val _biometricUnlockAvailable = MutableStateFlow(false)
    val biometricUnlockAvailable: StateFlow<Boolean> = _biometricUnlockAvailable.asStateFlow()

    /** A biometric unlock cipher waiting to be authorized by a prompt. */
    private val _bioUnlockCipher = MutableStateFlow<Cipher?>(null)
    val bioUnlockCipher: StateFlow<Cipher?> = _bioUnlockCipher.asStateFlow()

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
        _appState.value = if (VaultStore.isOnboarded(getApplication())) AppState.LOCKED else AppState.ONBOARDING
        refreshBiometricAvailability()
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
                "  attachments/         = ${File(VaultStore.vaultDir(app, vaultId), "attachments")}\n" +
                "  thumbnails/          = ${File(VaultStore.vaultDir(app, vaultId), "thumbnails")}\n" +
                "cacheDir (video temp)  = ${app.cacheDir}\n" +
                "vault index            = ${VaultStore.indexFile(app)} (exists=${VaultStore.indexFile(app).exists()})\n" +
                "model dir (external)   = ${File(app.getExternalFilesDir(null), MODEL_DIR)}\n" +
                "model dir (internal)   = ${File(app.filesDir, MODEL_DIR)}\n" +
                "shared prefs (device secret) = ${app.filesDir.parentFile}/shared_prefs/cryptowl.vault.xml\n" +
                "shared prefs (chat settings) = ${app.filesDir.parentFile}/shared_prefs/cryptowl.chat.settings.xml",
        )
    }

    // ------------------------------------------------------------ onboarding

    fun startOnboarding() {
        _appState.value = AppState.ONBOARDING
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
                _vaultCreated.value = true
            } catch (e: Throwable) {
                Log.e(TAG, "createVault failed", e)
                _vaultError.value = e.message ?: getApplication<Application>().getString(R.string.error_create_vault_failed)
            } finally {
                _creatingVault.value = false
            }
        }
    }

    /** Called by the biometric-setup screen once the optional step is done. */
    fun markVaultCreated() {
        masterPassword.getAndSet(null)?.clear()
        preparedBiometric.getAndSet(null)?.let { BioKeySetup(getApplication()).cancel(it) }
        _biometricCipher.value = null
        _biometricReady.value = false
        _appState.value = AppState.LOCKED
        refreshBiometricAvailability()
    }

    // ------------------------------------------------- fingerprint enrollment

    /** Prepares the biometric wrap (derives VaultKey, creates BioKey encrypt cipher). */
    fun prepareBiometric() {
        val password = masterPassword.get() ?: run {
            _biometricError.value = getApplication<Application>().getString(R.string.error_biometric_password_lost)
            return
        }
        prepareBiometricWrap(password, clearPassword = false)
    }

    /**
     * Enables fingerprint unlock from Settings: wraps the VaultKey with a fresh
     * BioKey. The entered master password is consumed and wiped here.
     */
    fun enableBiometric(password: ProtectedValue) {
        prepareBiometricWrap(password, clearPassword = true)
    }

    private fun prepareBiometricWrap(password: ProtectedValue, clearPassword: Boolean) {
        _biometricError.value = null
        if (BiometricManager.from(getApplication()).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            _biometricError.value = getApplication<Application>().getString(R.string.error_biometric_not_enrolled)
            if (clearPassword) password.clear()
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
            } catch (e: Throwable) {
                Log.e(TAG, "prepareBiometric failed", e)
                _biometricError.value = e.message ?: getApplication<Application>().getString(R.string.error_biometric_setup_failed)
            } finally {
                if (clearPassword) password.clear()
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
                _onboardingDone.value = true
                refreshBiometricAvailability()
            } catch (e: Throwable) {
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

    /** User chose to skip the optional fingerprint setup. */
    fun skipBiometric() {
        cancelBiometricPrompt()
        _onboardingDone.value = true
    }

    /**
     * Disables fingerprint unlock by destroying the Keystore BioKey. The
     * wrapped copy left in vault.meta is undecryptable afterwards; no master
     * password is needed because nothing usable remains.
     */
    fun disableBiometric() {
        _biometricError.value = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { BioKeyManager.deleteBioKey() }
                    .onFailure { Log.e(TAG, "deleteBioKey failed", it) }
            }
            refreshBiometricAvailability()
        }
    }

    fun clearBiometricError() {
        _biometricError.value = null
    }

    // ------------------------------------------------- master password change

    private val _passwordChangeBusy = MutableStateFlow(false)
    val passwordChangeBusy: StateFlow<Boolean> = _passwordChangeBusy.asStateFlow()

    private val _passwordChangeError = MutableStateFlow<String?>(null)
    val passwordChangeError: StateFlow<String?> = _passwordChangeError.asStateFlow()

    private val _passwordChanged = MutableStateFlow(false)
    val passwordChanged: StateFlow<Boolean> = _passwordChanged.asStateFlow()

    fun changeMasterPassword(current: String, new: String) {
        _passwordChangeBusy.value = true
        _passwordChangeError.value = null
        _passwordChanged.value = false
        viewModelScope.launch {
            val currentValue = ProtectedValue.fromString(current)
            val newValue = ProtectedValue.fromString(new)
            try {
                withContext(Dispatchers.IO) {
                    MasterPasswordChange(getApplication()).change(currentValue, newValue, vaultId)
                }
                _passwordChanged.value = true
            } catch (e: Throwable) {
                Log.e(TAG, "changeMasterPassword failed", e)
                _passwordChangeError.value = e.message
                    ?: getApplication<Application>().getString(R.string.error_password_change_failed)
            } finally {
                currentValue.clear()
                newValue.clear()
                _passwordChangeBusy.value = false
            }
        }
    }

    fun consumePasswordChanged() {
        _passwordChanged.value = false
    }

    fun clearPasswordChangeError() {
        _passwordChangeError.value = null
    }

    // ------------------------------------------------------------------ about

    private val _vaultInfo = MutableStateFlow<String?>(null)
    val vaultInfo: StateFlow<String?> = _vaultInfo.asStateFlow()

    fun loadVaultInfo() {
        viewModelScope.launch {
            _vaultInfo.value = withContext(Dispatchers.IO) {
                runCatching {
                    val meta = VaultMetaJson.decode(VaultStore.metaFile(getApplication(), vaultId).readText())
                    "vault ${meta.vaultId} · format v${meta.version} · " +
                        "argon2id m=${meta.kdf.mKib}KiB t=${meta.kdf.t} p=${meta.kdf.p}"
                }.getOrNull()
            }
        }
    }

    // ------------------------------------------------------ auto-lock / privacy

    private val settingsStore = SettingsStore(getApplication())

    private val _autoLockTimeoutMs = MutableStateFlow(settingsStore.autoLockTimeoutMs)
    val autoLockTimeoutMs: StateFlow<Long> = _autoLockTimeoutMs.asStateFlow()

    private val _flagSecure = MutableStateFlow(settingsStore.flagSecure)
    val flagSecure: StateFlow<Boolean> = _flagSecure.asStateFlow()

    private var autoLockJob: Job? = null
    private var backgroundedAtMs = 0L

    fun setAutoLockTimeout(timeoutMs: Long) {
        settingsStore.autoLockTimeoutMs = timeoutMs
        _autoLockTimeoutMs.value = timeoutMs
    }

    fun setFlagSecure(enabled: Boolean) {
        settingsStore.flagSecure = enabled
        _flagSecure.value = enabled
    }

    /** App moved to the background: lock now or after the configured timeout. */
    fun onAppBackgrounded() {
        if (_session.value == null) return
        backgroundedAtMs = System.currentTimeMillis()
        val timeout = _autoLockTimeoutMs.value
        if (AutoLockPolicy.shouldLockImmediately(timeout)) {
            lockVault()
            return
        }
        autoLockJob?.cancel()
        autoLockJob = viewModelScope.launch {
            delay(timeout)
            lockVault()
        }
    }

    /**
     * App returned to the foreground: cancel the pending lock unless the
     * process was frozen past the timeout (the delayed job may not have run).
     */
    fun onAppForegrounded() {
        autoLockJob?.cancel()
        autoLockJob = null
        if (_session.value != null && backgroundedAtMs > 0) {
            val elapsed = System.currentTimeMillis() - backgroundedAtMs
            if (AutoLockPolicy.shouldLockOnResume(elapsed, _autoLockTimeoutMs.value)) {
                lockVault()
            }
        }
        backgroundedAtMs = 0L
    }

    // ------------------------------------------------------------ vault unlock

    private fun refreshBiometricAvailability() {
        viewModelScope.launch {
            _biometricUnlockAvailable.value = withContext(Dispatchers.IO) {
                runCatching { UnlockService(getApplication()).hasBiometricUnlock(vaultId) }.getOrDefault(false)
            }
        }
    }

    fun clearUnlockError() {
        _unlockError.value = null
    }

    /** Prepares a biometric unlock cipher; the lock screen then shows a prompt. */
    fun requestBiometricUnlock() {
        _unlockError.value = null
        viewModelScope.launch {
            try {
                val cipher = withContext(Dispatchers.IO) {
                    UnlockService(getApplication()).prepareBiometricUnlock(vaultId)
                }
                _bioUnlockCipher.value = cipher
            } catch (e: Throwable) {
                Log.e(TAG, "requestBiometricUnlock failed", e)
                // Covers a Keystore key invalidated by a biometric enrollment
                // change as well as corrupt metadata: fall back to the password.
                _unlockError.value = getApplication<Application>().getString(R.string.error_biometric_unavailable)
            }
        }
    }

    fun cancelBiometricUnlock() {
        _bioUnlockCipher.value = null
    }

    /** Opens the vault with a biometric-authorized cipher. */
    fun unlockWithBiometric(cipher: Cipher) {
        _unlocking.value = true
        _unlockError.value = null
        viewModelScope.launch {
            try {
                val session = withContext(Dispatchers.IO) {
                    UnlockService(getApplication()).unlockWithBiometric(cipher, vaultId)
                }
                _bioUnlockCipher.value = null
                openSession(session)
            } catch (e: Throwable) {
                Log.e(TAG, "unlockWithBiometric failed", e)
                _unlockError.value = e.message ?: getApplication<Application>().getString(R.string.error_unlock_failed)
            } finally {
                _unlocking.value = false
            }
        }
    }

    fun unlockVault(password: ProtectedValue) {
        _unlocking.value = true
        _unlockError.value = null
        viewModelScope.launch {
            try {
                val session = withContext(Dispatchers.IO) {
                    UnlockService(getApplication()).unlock(password)
                }
                openSession(session)
            } catch (e: Throwable) {
                Log.e(TAG, "unlockVault failed", e)
                _unlockError.value = e.message ?: getApplication<Application>().getString(R.string.error_unlock_failed)
            } finally {
                _unlocking.value = false
            }
        }
    }

    private fun openSession(session: VaultSession) {
        Log.d(TAG, "openSession: vaultId=${session.vaultId} (session keys held until lock)")
        _session.value?.close()
        _session.value = session
        _appState.value = AppState.UNLOCKED
    }

    fun lockVault() {
        Log.d(TAG, "lockVault: wiping session + closing db")
        _session.value?.close()
        _session.value = null
        _unlockError.value = null
        _appState.value = AppState.LOCKED
    }

    // --------------------------------------------------- restore / backup

    fun openRestore() {
        _restoreError.value = null
        _restoreProgress.value = null
    }

    /** Restores the vault picked via SAF (a desktop-produced vault folder). */
    fun restoreVault(treeUri: Uri, onDone: (Boolean) -> Unit = {}) {
        _restoreError.value = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    VaultBackup(getApplication()).restore(treeUri, vaultId) { _restoreProgress.value = it }
                }
                _appState.value = AppState.LOCKED
                refreshBiometricAvailability()
                onDone(true)
            } catch (e: Throwable) {
                Log.e(TAG, "restoreVault failed", e)
                _restoreError.value = e.message
                    ?: getApplication<Application>().getString(R.string.error_restore_failed)
                onDone(false)
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
            } catch (e: Throwable) {
                Log.e(TAG, "backupVault failed", e)
                _backupError.value = e.message
                    ?: getApplication<Application>().getString(R.string.error_backup_failed)
            } finally {
                _backupProgress.value = null
            }
        }
    }

    override fun onCleared() {
        chat.close()
    }
}
