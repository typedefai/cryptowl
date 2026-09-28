package com.typedefai.cryptowl

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.typedefai.cryptowl.crypto.KdfService
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.vault.KekService
import com.typedefai.cryptowl.vault.PasswordDetail
import com.typedefai.cryptowl.vault.PasswordDraft
import com.typedefai.cryptowl.vault.PasswordService
import com.typedefai.cryptowl.vault.PasswordSummary
import com.typedefai.cryptowl.vault.VaultSession
import javax.crypto.Cipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Secret-tier password CRUD orchestration. Every create/read/update/delete
 * runs under a fresh fingerprint: the pending action is queued, a biometric
 * cipher is prepared (create-KEK if the vault has none, otherwise unlock-KEK),
 * the UI shows the prompt, and [completeBiometric] executes the action and
 * wipes the KEK immediately afterwards — nothing is cached between accesses.
 */
class PasswordViewModel(app: Application) : AndroidViewModel(app) {

    private sealed interface PendingAction {
        data class Reveal(val id: String) : PendingAction
        data class Create(val draft: PasswordDraft) : PendingAction
        data class Update(val draft: PasswordDraft) : PendingAction
        data class Delete(val id: String) : PendingAction
    }

    private val kdf = KdfService()

    @Volatile
    private var session: VaultSession? = null
    private var pending: PendingAction? = null

    private val _entries = MutableStateFlow<List<PasswordSummary>>(emptyList())
    val entries: StateFlow<List<PasswordSummary>> = _entries.asStateFlow()

    private val _detail = MutableStateFlow<PasswordDetail?>(null)
    val detail: StateFlow<PasswordDetail?> = _detail.asStateFlow()

    /** null = not probed yet; false = no `kek:biokey` row in this vault. */
    private val _kekAvailable = MutableStateFlow<Boolean?>(null)
    val kekAvailable: StateFlow<Boolean?> = _kekAvailable.asStateFlow()

    /** Cipher waiting for the biometric prompt; null when idle. */
    private val _pendingCipher = MutableStateFlow<Cipher?>(null)
    val pendingCipher: StateFlow<Cipher?> = _pendingCipher.asStateFlow()

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
            _pendingCipher.value = null
            _detail.value = null
            refresh()
        }
    }

    fun detach() {
        session = null
        pending = null
        _pendingCipher.value = null
        _detail.value = null
        _entries.value = emptyList()
        _kekAvailable.value = null
    }

    fun refresh() {
        val db = session?.db ?: return
        viewModelScope.launch {
            try {
                val (list, hasKek) = withContext(Dispatchers.IO) {
                    PasswordService(db, kdf).list() to KekService(db).exists()
                }
                _entries.value = list
                _kekAvailable.value = hasKek
            } catch (e: Throwable) {
                Log.e(TAG, "refresh failed", e)
                _error.value = message(e, R.string.passwords_error_operation)
            }
        }
    }

    // ------------------------------------------------------------- actions

    fun reveal(id: String) = request(PendingAction.Reveal(id))

    fun save(draft: PasswordDraft) =
        request(if (draft.id == null) PendingAction.Create(draft) else PendingAction.Update(draft))

    fun delete(id: String) = request(PendingAction.Delete(id))

    fun clearDetail() {
        _detail.value = null
    }

    fun consumeSaved() {
        _saved.value = false
    }

    fun clearError() {
        _error.value = null
    }

    private fun request(action: PendingAction) {
        val db = session?.db ?: return
        _error.value = null
        _saved.value = false
        pending = action
        viewModelScope.launch {
            try {
                val hasKek = withContext(Dispatchers.IO) { KekService(db).exists() }
                _kekAvailable.value = hasKek
                val cipher = withContext(Dispatchers.IO) {
                    val kekService = KekService(db)
                    if (hasKek) kekService.prepareUnlockCipher() else kekService.prepareCreateCipher()
                }
                _pendingCipher.value = cipher
            } catch (e: Throwable) {
                Log.e(TAG, "prepare biometric access failed", e)
                pending = null
                _error.value = message(e, R.string.passwords_error_fingerprint)
            }
        }
    }

    /** Runs the queued action with the biometric-authorized cipher. */
    fun completeBiometric(cipher: Cipher) {
        val db = session?.db ?: return
        val action = pending
        pending = null
        _pendingCipher.value = null
        viewModelScope.launch {
            _busy.value = true
            try {
                val kek = withContext(Dispatchers.IO) {
                    val kekService = KekService(db)
                    if (kekService.exists()) kekService.unlock(cipher) else kekService.completeCreate(cipher)
                }
                _kekAvailable.value = true
                try {
                    withContext(Dispatchers.IO) { execute(db, action, kek) }
                } finally {
                    kek.clear()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "password operation failed", e)
                _error.value = message(e, R.string.passwords_error_operation)
            } finally {
                _busy.value = false
                refresh()
            }
        }
    }

    fun cancelBiometric() {
        pending = null
        _pendingCipher.value = null
    }

    private fun execute(db: net.zetetic.database.sqlcipher.SQLiteDatabase, action: PendingAction?, kek: ProtectedValue) {
        val service = PasswordService(db, kdf)
        when (action) {
            is PendingAction.Reveal -> _detail.value = service.detail(kek, action.id)
            is PendingAction.Create -> {
                service.create(kek, action.draft)
                _saved.value = true
            }
            is PendingAction.Update -> {
                service.update(kek, action.draft)
                _detail.value = action.draft.id?.let { service.detail(kek, it) }
                _saved.value = true
            }
            is PendingAction.Delete -> {
                service.softDelete(action.id)
                _detail.value = null
            }
            null -> Unit
        }
    }

    private fun message(e: Throwable, fallbackRes: Int): String =
        e.message ?: getApplication<Application>().getString(fallbackRes)

    private companion object {
        const val TAG = "cwl:PasswordViewModel"
    }
}
