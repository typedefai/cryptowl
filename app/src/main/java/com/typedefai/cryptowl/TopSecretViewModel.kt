package com.typedefai.cryptowl

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.vault.NoteDraft
import com.typedefai.cryptowl.vault.NoteSummary
import com.typedefai.cryptowl.vault.TopSecretNote
import com.typedefai.cryptowl.vault.TopSecretService
import com.typedefai.cryptowl.vault.VaultSession
import javax.crypto.Cipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Top-Secret tier orchestration: every access is a two-step gate —
 * fingerprint (BioKey unwraps the biometric TS-KEK copy) then the typed
 * secondary password (must re-derive the same TS-KEK before the
 * TopSecretKEK is unwrapped). Keys are wiped after each action.
 */
class TopSecretViewModel(app: Application) : AndroidViewModel(app) {

    private sealed interface Pending {
        data class Setup(val secondaryPassword: ProtectedValue) : Pending
        data class Reveal(val id: String) : Pending
        data class Create(val draft: NoteDraft) : Pending
        data class Update(val draft: NoteDraft) : Pending
        data class Delete(val id: String) : Pending
    }

    @Volatile
    private var session: VaultSession? = null
    private var pending: Pending? = null

    /** Fingerprint factor result, held only between the two gate steps. */
    private var tsKekBio: ProtectedValue? = null

    private val _entries = MutableStateFlow<List<NoteSummary>>(emptyList())
    val entries: StateFlow<List<NoteSummary>> = _entries.asStateFlow()

    private val _detail = MutableStateFlow<TopSecretNote?>(null)
    val detail: StateFlow<TopSecretNote?> = _detail.asStateFlow()

    private val _configured = MutableStateFlow<Boolean?>(null)
    val configured: StateFlow<Boolean?> = _configured.asStateFlow()

    private val _pendingCipher = MutableStateFlow<Cipher?>(null)
    val pendingCipher: StateFlow<Cipher?> = _pendingCipher.asStateFlow()

    private val _awaitingPassword = MutableStateFlow(false)
    val awaitingPassword: StateFlow<Boolean> = _awaitingPassword.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun attach(newSession: VaultSession) {
        if (session !== newSession) {
            session = newSession
            pending = null
            clearKeys()
            _detail.value = null
            refresh()
        }
    }

    fun detach() {
        session = null
        pending = null
        clearKeys()
        _entries.value = emptyList()
        _detail.value = null
        _configured.value = null
    }

    fun refresh() {
        val db = session?.db ?: return
        viewModelScope.launch {
            try {
                val (list, configured) = withContext(Dispatchers.IO) {
                    val service = TopSecretService(getApplication(), db)
                    service.list() to service.isConfigured()
                }
                _entries.value = list
                _configured.value = configured
            } catch (e: Throwable) {
                Log.e(TAG, "refresh failed", e)
                _error.value = message(e)
            }
        }
    }

    fun setup(secondaryPassword: ProtectedValue) {
        val db = session?.db ?: return
        _error.value = null
        pending = Pending.Setup(secondaryPassword)
        viewModelScope.launch {
            try {
                val cipher = withContext(Dispatchers.IO) {
                    TopSecretService(getApplication(), db).prepareSetupCipher()
                }
                _pendingCipher.value = cipher
            } catch (e: Throwable) {
                Log.e(TAG, "prepare setup cipher failed", e)
                pending = null
                secondaryPassword.clear()
                _error.value = message(e)
            }
        }
    }

    fun reveal(id: String) = requestAccess(Pending.Reveal(id))

    fun save(draft: NoteDraft) =
        requestAccess(if (draft.id == null) Pending.Create(draft) else Pending.Update(draft))

    fun delete(id: String) = requestAccess(Pending.Delete(id))

    fun consumeSaved() {
        _saved.value = false
    }

    fun clearDetail() {
        _detail.value = null
    }

    fun clearError() {
        _error.value = null
    }

    /** Step 1 done (fingerprint); the UI should now ask for the password. */
    fun completeFingerprint(cipher: Cipher) {
        val db = session?.db ?: return
        val action = pending
        _pendingCipher.value = null
        viewModelScope.launch {
            try {
                val service = TopSecretService(getApplication(), db)
                if (action is Pending.Setup) {
                    withContext(Dispatchers.IO) { service.completeSetup(cipher, action.secondaryPassword) }
                    action.secondaryPassword.clear()
                    pending = null
                    _configured.value = true
                    refresh()
                } else {
                    tsKekBio = withContext(Dispatchers.IO) { service.unlockTsKek(cipher) }
                    _awaitingPassword.value = true
                }
            } catch (e: Throwable) {
                Log.e(TAG, "fingerprint factor failed", e)
                (action as? Pending.Setup)?.secondaryPassword?.clear()
                pending = null
                _error.value = message(e)
            }
        }
    }

    /** Step 2: verify the secondary password and run the queued action. */
    fun submitSecondaryPassword(secondaryPassword: String) {
        val db = session?.db ?: return
        val action = pending ?: return
        val kekBio = tsKekBio ?: return
        val passwordValue = ProtectedValue.fromString(secondaryPassword)
        _error.value = null
        viewModelScope.launch {
            _busy.value = true
            try {
                val topSecretKek = withContext(Dispatchers.IO) {
                    val service = TopSecretService(getApplication(), db)
                    try {
                        service.unlockTopSecretKek(kekBio, passwordValue)
                    } catch (e: Throwable) {
                        // wrong password: keep the gate open for a retry
                        _error.value = message(e)
                        null
                    }
                }
                if (topSecretKek != null) {
                    try {
                        withContext(Dispatchers.IO) { execute(action, topSecretKek) }
                        _awaitingPassword.value = false
                        clearKeys()
                        pending = null
                    } finally {
                        topSecretKek.clear()
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "secondary password step failed", e)
                _error.value = message(e)
            } finally {
                passwordValue.clear()
                _busy.value = false
                refresh()
            }
        }
    }

    fun cancelAccess() {
        (pending as? Pending.Setup)?.secondaryPassword?.clear()
        pending = null
        _pendingCipher.value = null
        _awaitingPassword.value = false
        clearKeys()
    }

    private fun requestAccess(action: Pending) {
        val db = session?.db ?: return
        _error.value = null
        pending = action
        viewModelScope.launch {
            try {
                val cipher = withContext(Dispatchers.IO) {
                    TopSecretService(getApplication(), db).prepareUnlockCipher()
                }
                _pendingCipher.value = cipher
            } catch (e: Throwable) {
                Log.e(TAG, "prepare access cipher failed", e)
                pending = null
                _error.value = message(e)
            }
        }
    }

    private fun execute(action: Pending, topSecretKek: ProtectedValue) {
        val db = session?.db ?: return
        val service = TopSecretService(getApplication(), db)
        when (action) {
            is Pending.Reveal -> _detail.value = service.read(action.id, topSecretKek)
            is Pending.Create -> {
                service.create(action.draft.title, action.draft.content, topSecretKek)
                _saved.value = true
            }
            is Pending.Update -> {
                service.update(action.draft.id!!, action.draft.title, action.draft.content, topSecretKek)
                _detail.value = service.read(action.draft.id, topSecretKek)
                _saved.value = true
            }
            is Pending.Delete -> {
                service.delete(action.id)
                _detail.value = null
            }
            is Pending.Setup -> Unit
        }
    }

    private fun clearKeys() {
        tsKekBio?.clear()
        tsKekBio = null
    }

    private fun message(e: Throwable): String =
        e.message ?: getApplication<Application>().getString(R.string.top_secret_error)

    private companion object {
        const val TAG = "cwl:TopSecretViewModel"
    }
}
