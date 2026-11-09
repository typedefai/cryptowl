package com.typedefai.cryptowl

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.vault.TopSecretService
import com.typedefai.cryptowl.vault.VaultSession
import javax.crypto.Cipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Top-Secret setup orchestration (Settings → Set up secondary password).
 *
 * Setup re-enters the master password: the `secondary` salt is added to
 * `vault.meta` and its MAC (SMK[32:64]) is re-signed, exactly like the
 * desktop client. The actual T-item gate lives in the editor/history VMs.
 */
class TopSecretViewModel(app: Application) : AndroidViewModel(app) {

    data class UiState(
        val configured: Boolean? = null,
        val busy: Boolean = false,
        val pendingCipher: Cipher? = null,
        val error: String? = null,
        val done: Boolean = false,
    )

    @Volatile
    private var session: VaultSession? = null
    private var secondaryPassword: ProtectedValue? = null
    private var masterPassword: ProtectedValue? = null

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun attach(newSession: VaultSession) {
        if (session !== newSession) {
            session = newSession
            refresh()
        }
    }

    fun detach() {
        session = null
        clearPending()
        _state.value = UiState()
    }

    fun refresh() {
        val current = session ?: return
        viewModelScope.launch {
            try {
                val configured = withContext(Dispatchers.IO) {
                    TopSecretService(getApplication(), current.db).isConfigured()
                }
                _state.update { it.copy(configured = configured) }
            } catch (t: Throwable) {
                Log.e(TAG, "refresh failed", t)
                _state.update { it.copy(error = t.message) }
            }
        }
    }

    fun startSetup(secondaryPasswordText: String, masterPasswordText: String) {
        val current = session ?: return
        clearPending()
        secondaryPassword = ProtectedValue.fromString(secondaryPasswordText)
        masterPassword = ProtectedValue.fromString(masterPasswordText)
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val cipher = withContext(Dispatchers.IO) {
                    TopSecretService(getApplication(), current.db).prepareSetupCipher()
                }
                _state.update { it.copy(busy = false, pendingCipher = cipher) }
            } catch (t: Throwable) {
                Log.e(TAG, "prepare setup cipher failed", t)
                clearPending()
                _state.update { it.copy(busy = false, error = t.message) }
            }
        }
    }

    /** BiometricPrompt succeeded: finish the setup with the authorized cipher. */
    fun completeSetup(authorizedCipher: Cipher) {
        val current = session ?: return
        val secondary = secondaryPassword
        val master = masterPassword
        if (secondary == null || master == null) return
        _state.update { it.copy(busy = true, pendingCipher = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    TopSecretService(getApplication(), current.db)
                        .completeSetup(authorizedCipher, secondary, master)
                }
                _state.update { it.copy(busy = false, configured = true, done = true) }
            } catch (t: Throwable) {
                Log.e(TAG, "completeSetup failed", t)
                _state.update { it.copy(busy = false, error = t.message) }
            } finally {
                clearPending()
            }
        }
    }

    fun cancelPrompt() {
        clearPending()
        _state.update { it.copy(pendingCipher = null, busy = false) }
    }

    fun consumeDone() = _state.update { it.copy(done = false) }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun clearPending() {
        secondaryPassword?.clear()
        masterPassword?.clear()
        secondaryPassword = null
        masterPassword = null
    }

    override fun onCleared() {
        clearPending()
    }

    private companion object {
        const val TAG = "cwl:TopSecretViewModel"
    }
}
