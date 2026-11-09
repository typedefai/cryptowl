package com.typedefai.cryptowl

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.typedefai.cryptowl.crypto.CryptoLog
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.model.Classification
import com.typedefai.cryptowl.model.ItemType
import com.typedefai.cryptowl.model.ItemUpdate
import com.typedefai.cryptowl.model.NewItem
import com.typedefai.cryptowl.vault.ItemRepository
import com.typedefai.cryptowl.vault.SecretAccess
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
import org.json.JSONObject

/**
 * Item editor state machine: loads metadata, gates S/T payloads behind the
 * per-access key flow, and writes C/S/T items through [ItemRepository].
 *
 * Keys are held for the lifetime of the editor screen only and wiped on
 * [clearKeys] (screen disposal, save completion).
 */
class ItemEditorViewModel(app: Application) : AndroidViewModel(app) {

    enum class KeyPurpose { KEK, TOP_SECRET }

    data class UiState(
        val id: String? = null,
        val type: String = ItemType.PLAIN.id,
        val title: String = "",
        val classification: Classification = Classification.C,
        val content: String = "",
        val username: String = "",
        val url: String = "",
        val secret: String = "",
        val notes: String = "",
        val exists: Boolean = false,
        val locked: Boolean = false,
        val busy: Boolean = false,
        val error: String? = null,
        val pendingCipher: Cipher? = null,
        val keyPurpose: KeyPurpose? = null,
        val awaitingMasterPassword: Boolean = false,
        val awaitingSecondaryPassword: Boolean = false,
        val saved: Boolean = false,
        val deleted: Boolean = false,
    )

    @Volatile
    private var session: VaultSession? = null
    private var kek: ProtectedValue? = null
    private var topSecretKek: ProtectedValue? = null
    private var tsKekBio: ProtectedValue? = null
    private var pendingKek: SecretAccess.KekPreparation? = null
    private var pendingSave = false

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun attach(newSession: VaultSession) {
        if (session !== newSession) {
            session = newSession
        }
    }

    // ------------------------------------------------------------------ open

    fun open(id: String?, typeId: String?) {
        val current = session ?: return
        clearKeys()
        if (id == null) {
            val type = ItemType.fromId(typeId ?: ItemType.PLAIN.id)
            _state.value = UiState(
                type = type.id,
                classification = type.defaultClassification,
                exists = false,
                locked = false,
            )
            return
        }
        _state.value = UiState(id = id, busy = true)
        viewModelScope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    ItemRepository(current.db, current.hfk).getSummary(id)
                }
                val type = ItemType.fromId(summary.type)
                if (summary.classification == Classification.C) {
                    val record = withContext(Dispatchers.IO) {
                        ItemRepository(current.db, current.hfk).get(id)
                    }
                    _state.value = record.toUiState(type)
                } else {
                    _state.value = UiState(
                        id = id,
                        type = type.id,
                        title = summary.title,
                        classification = summary.classification,
                        exists = true,
                        locked = true,
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "open failed", t)
                _state.update { it.copy(busy = false, error = message(t)) }
            }
        }
    }

    // --------------------------------------------------------------- editing

    fun updateTitle(value: String) = _state.update { it.copy(title = value) }

    fun updateContent(value: String) = _state.update { it.copy(content = value) }

    fun updateUsername(value: String) = _state.update { it.copy(username = value) }

    fun updateUrl(value: String) = _state.update { it.copy(url = value) }

    fun updateSecret(value: String) = _state.update { it.copy(secret = value) }

    fun updateNotes(value: String) = _state.update { it.copy(notes = value) }

    fun updateClassification(value: Classification) {
        if (_state.value.exists && _state.value.locked && value != _state.value.classification) {
            // Changing the tier of a locked S/T item needs the old key first.
            _state.update { it.copy(error = "unlock the item before changing its tier") }
            return
        }
        _state.update { it.copy(classification = value) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun consumeSaved() = _state.update { it.copy(saved = false) }

    // ------------------------------------------------------------ secret gate

    /** Starts the S/T authorization flow (BiometricPrompt + optional factors). */
    fun requestUnlock() {
        val current = session ?: return
        val st = _state.value
        if (st.classification == Classification.C) {
            if (st.exists) loadPayload() else if (pendingSave) {
                pendingSave = false
                save()
            }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                when (st.classification) {
                    Classification.S -> {
                        val access = SecretAccess(getApplication(), current)
                        val preparation = withContext(Dispatchers.IO) { access.prepareKek() }
                        pendingKek = preparation
                        _state.update {
                            it.copy(
                                busy = false,
                                pendingCipher = preparation.cipher,
                                keyPurpose = KeyPurpose.KEK,
                            )
                        }
                    }
                    Classification.T -> {
                        val cipher = withContext(Dispatchers.IO) {
                            TopSecretService(getApplication(), current.db).prepareUnlockCipher()
                        }
                        _state.update {
                            it.copy(busy = false, pendingCipher = cipher, keyPurpose = KeyPurpose.TOP_SECRET)
                        }
                    }
                    Classification.C -> loadPayload()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "requestUnlock failed", t)
                _state.update { it.copy(busy = false, error = message(t)) }
            }
        }
    }

    /** BiometricPrompt succeeded for [cipher]. */
    fun onBiometricAuthorized(cipher: Cipher) {
        val current = session ?: return
        val st = _state.value
        _state.update { it.copy(pendingCipher = null) }
        when (st.keyPurpose) {
            KeyPurpose.TOP_SECRET -> {
                viewModelScope.launch {
                    try {
                        tsKekBio = withContext(Dispatchers.IO) {
                            TopSecretService(getApplication(), current.db).unlockTsKek(cipher)
                        }
                        _state.update { it.copy(awaitingSecondaryPassword = true) }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Top Secret fingerprint factor failed", t)
                        _state.update { it.copy(error = message(t)) }
                    }
                }
            }
            KeyPurpose.KEK -> {
                val preparation = pendingKek ?: return
                if (preparation.mode == SecretAccess.KekMode.IMPORT) {
                    _state.update { it.copy(awaitingMasterPassword = true, pendingCipher = cipher) }
                } else {
                    completeKek(preparation, cipher, null)
                }
            }
            null -> Unit
        }
    }

    fun onBiometricCancelled() {
        _state.update { it.copy(pendingCipher = null, keyPurpose = null, busy = false) }
    }

    /** No enrolled fingerprint: Secret-tier access needs one (docs/design.md L2). */
    fun onBiometricUnavailable() {
        _state.update {
            it.copy(
                pendingCipher = null,
                keyPurpose = null,
                awaitingMasterPassword = false,
                awaitingSecondaryPassword = false,
                busy = false,
                error = getApplication<Application>().getString(R.string.error_biometric_required),
            )
        }
    }

    /** Master-password fallback for importing a desktop `kek:smk` binding. */
    fun submitMasterPassword(password: String) {
        val preparation = pendingKek ?: return
        val cipher = _state.value.pendingCipher ?: return
        // Ownership passes to the coroutine: it uses the value and clears it.
        val value = ProtectedValue.fromString(password)
        _state.update { it.copy(busy = true, awaitingMasterPassword = false, pendingCipher = null) }
        completeKek(preparation, cipher, value, clearPasswordAfter = true)
    }

    /** Second factor of the T-tier gate. */
    fun submitSecondaryPassword(password: String) {
        val current = session ?: return
        val bio = tsKekBio ?: return
        val value = ProtectedValue.fromString(password)
        _state.update { it.copy(busy = true, awaitingSecondaryPassword = false) }
        viewModelScope.launch {
            try {
                val unlocked = withContext(Dispatchers.IO) {
                    TopSecretService(getApplication(), current.db).unlockTopSecretKek(bio, value)
                }
                topSecretKek?.clear()
                topSecretKek = unlocked
                bio.clear()
                tsKekBio = null
                loadPayload()
            } catch (t: Throwable) {
                Log.e(TAG, "secondary password failed", t)
                _state.update { it.copy(busy = false, error = message(t), awaitingSecondaryPassword = true) }
            } finally {
                value.clear()
            }
        }
    }

    fun cancelGate() {
        clearKeys()
        _state.update {
            it.copy(
                pendingCipher = null,
                keyPurpose = null,
                awaitingMasterPassword = false,
                awaitingSecondaryPassword = false,
                busy = false,
            )
        }
    }

    private fun completeKek(
        preparation: SecretAccess.KekPreparation,
        cipher: Cipher,
        masterPassword: ProtectedValue?,
        clearPasswordAfter: Boolean = false,
    ) {
        val current = session ?: return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val access = SecretAccess(getApplication(), current)
                val unlocked = withContext(Dispatchers.IO) {
                    access.completeKek(preparation, cipher, masterPassword)
                }
                kek?.clear()
                kek = unlocked
                pendingKek = null
                loadPayload()
            } catch (t: Throwable) {
                Log.e(TAG, "KEK unlock failed", t)
                _state.update { it.copy(busy = false, error = message(t)) }
            } finally {
                if (clearPasswordAfter) masterPassword?.clear()
            }
        }
    }

    private fun loadPayload() {
        val current = session ?: return
        val st = _state.value
        val id = st.id
        if (id == null) {
            // New item: the key is now available, continue the pending save.
            if (pendingSave) {
                pendingSave = false
                save()
            }
            return
        }
        viewModelScope.launch {
            try {
                val record = withContext(Dispatchers.IO) {
                    ItemRepository(current.db, current.hfk).get(id, kek, topSecretKek)
                }
                val type = ItemType.fromId(record.type)
                _state.value = record.toUiState(type)
                if (pendingSave) {
                    pendingSave = false
                    save()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "loadPayload failed", t)
                _state.update { it.copy(busy = false, error = message(t)) }
            }
        }
    }

    // ------------------------------------------------------------------ save

    fun save() {
        val current = session ?: return
        val st = _state.value
        val type = ItemType.fromId(st.type)
        if (st.title.isBlank()) {
            _state.update { it.copy(error = "title is required") }
            return
        }
        if (type == ItemType.PASSWORD && !st.exists && st.secret.isEmpty()) {
            _state.update { it.copy(error = "the password must not be empty") }
            return
        }
        val needsKek = st.classification == Classification.S && kek == null
        val needsTopSecret = st.classification == Classification.T && topSecretKek == null
        if (needsKek || needsTopSecret) {
            pendingSave = true
            requestUnlock()
            return
        }
        val payload = if (type == ItemType.PASSWORD) passwordPayload(st) else st.content
        // Desktop UI convention: non-secret L1 metadata lives in a JSON object.
        val meta = if (type == ItemType.PASSWORD) passwordMeta(st) else "{}"
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val repo = ItemRepository(current.db, current.hfk)
                    if (st.id == null) {
                        repo.create(
                            NewItem(
                                type = type.id,
                                classification = st.classification,
                                title = st.title,
                                meta = meta,
                                payload = payload,
                            ),
                            kek,
                            topSecretKek,
                        )
                    } else {
                        repo.update(
                            st.id,
                            ItemUpdate(
                                title = st.title,
                                classification = st.classification,
                                meta = meta,
                                payload = payload,
                            ),
                            kek,
                            topSecretKek,
                        )
                    }
                }
                _state.update { it.copy(busy = false, saved = true) }
            } catch (t: Throwable) {
                Log.e(TAG, "save failed", t)
                _state.update { it.copy(busy = false, error = message(t)) }
            }
        }
    }

    fun delete() {
        val current = session ?: return
        val id = _state.value.id ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ItemRepository(current.db, current.hfk).softDelete(id)
                }
                _state.update { it.copy(busy = false, deleted = true) }
            } catch (t: Throwable) {
                Log.e(TAG, "delete failed", t)
                _state.update { it.copy(busy = false, error = message(t)) }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    fun clearKeys() {
        kek?.clear()
        topSecretKek?.clear()
        tsKekBio?.clear()
        kek = null
        topSecretKek = null
        tsKekBio = null
        pendingKek = null
        pendingSave = false
    }

    /** True once an S/T payload is decrypted and editable. */
    fun isUnlocked(): Boolean {
        val st = _state.value
        return !st.locked || when (st.classification) {
            Classification.S -> kek != null
            Classification.T -> topSecretKek != null
            Classification.C -> true
        }
    }

    private fun com.typedefai.cryptowl.model.ItemRecord.toUiState(type: ItemType): UiState {
        val base = UiState(
            id = id,
            type = type.id,
            title = title,
            classification = classification,
            content = if (type == ItemType.PASSWORD) "" else payload,
            exists = true,
            locked = false,
        )
        if (type != ItemType.PASSWORD) return base
        return try {
            val json = JSONObject(payload)
            base.copy(
                username = json.optString("username"),
                url = json.optString("url"),
                secret = json.optString("secret"),
                notes = json.optString("notes"),
            )
        } catch (e: Exception) {
            CryptoLog.w(TAG, "password payload is not JSON; showing it as notes")
            base.copy(notes = payload)
        }
    }

    private fun passwordPayload(st: UiState): String = JSONObject().apply {
        put("notes", st.notes)
        put("secret", st.secret)
        put("url", st.url)
        put("username", st.username)
    }.toString()

    private fun passwordMeta(st: UiState): String = JSONObject().apply {
        put("url", st.url)
        put("username", st.username)
    }.toString()

    private fun message(t: Throwable): String =
        t.message ?: getApplication<Application>().getString(R.string.error_generic)

    override fun onCleared() {
        clearKeys()
    }

    private companion object {
        const val TAG = "cwl:ItemEditor"
    }
}
