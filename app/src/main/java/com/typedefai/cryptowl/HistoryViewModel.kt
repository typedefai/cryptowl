package com.typedefai.cryptowl

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.typedefai.cryptowl.crypto.ProtectedValue
import com.typedefai.cryptowl.model.Classification
import com.typedefai.cryptowl.model.ItemType
import com.typedefai.cryptowl.model.ItemVersionSummary
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

/**
 * Version history for one item: list snapshots, decrypt one for a diff against
 * the current payload, and restore it as a new version.
 *
 * S/T snapshots require the item's per-access key; the gate mirrors the
 * editor (BiometricPrompt for S, two-factor for T).
 */
class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    data class UiState(
        val itemId: String = "",
        val itemTitle: String = "",
        val itemType: String = ItemType.PLAIN.id,
        val currentClassification: Classification = Classification.C,
        val versions: List<ItemVersionSummary> = emptyList(),
        val selectedSeq: Int? = null,
        val selectedVersion: ItemVersionSummary? = null,
        val diff: List<DiffLine> = emptyList(),
        val busy: Boolean = false,
        val error: String? = null,
        val pendingCipher: Cipher? = null,
        val topSecretFingerprint: Boolean = false,
        val awaitingMasterPassword: Boolean = false,
        val awaitingSecondaryPassword: Boolean = false,
        val restored: Boolean = false,
    )

    @Volatile
    private var session: VaultSession? = null
    private var kek: ProtectedValue? = null
    private var topSecretKek: ProtectedValue? = null
    private var tsKekBio: ProtectedValue? = null
    private var pendingKek: SecretAccess.KekPreparation? = null
    private var pendingSeq: Int? = null

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun attach(newSession: VaultSession) {
        if (session !== newSession) {
            session = newSession
        }
    }

    fun load(itemId: String) {
        val current = session ?: return
        clearKeys()
        _state.value = UiState(itemId = itemId, busy = true)
        viewModelScope.launch {
            try {
                val (summary, versions) = withContext(Dispatchers.IO) {
                    val repo = ItemRepository(current.db, current.hfk)
                    repo.getSummary(itemId) to repo.versions(itemId)
                }
                _state.value = UiState(
                    itemId = itemId,
                    itemTitle = summary.title,
                    itemType = summary.type,
                    currentClassification = summary.classification,
                    versions = versions,
                    busy = false,
                )
            } catch (t: Throwable) {
                Log.e(TAG, "load failed", t)
                _state.update { it.copy(busy = false, error = t.message) }
            }
        }
    }

    /** Shows the diff for [seq]; prompts for the tier key when required. */
    fun selectVersion(seq: Int) {
        val snapshot = _state.value.versions.firstOrNull { it.seq == seq } ?: return
        pendingSeq = seq
        if (snapshot.classification.isSecret || _state.value.currentClassification.isSecret) {
            requestGate()
        } else {
            loadDiff(seq)
        }
    }

    fun restore(seq: Int) {
        val current = session ?: return
        pendingSeq = seq
        val snapshot = _state.value.versions.firstOrNull { it.seq == seq } ?: return
        if (snapshot.classification.isSecret || _state.value.currentClassification.isSecret) {
            requestGate()
            pendingRestore = true
            return
        }
        doRestore(seq)
    }

    private var pendingRestore = false

    // ---------------------------------------------------------------- gate

    private fun requestGate() {
        val current = session ?: return
        val target = _state.value.versions.firstOrNull { it.seq == pendingSeq }
        val needsTopSecret = target?.classification == Classification.T ||
            _state.value.currentClassification == Classification.T
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                if (needsTopSecret) {
                    val cipher = withContext(Dispatchers.IO) {
                        TopSecretService(getApplication(), current.db).prepareUnlockCipher()
                    }
                    _state.update {
                        it.copy(busy = false, pendingCipher = cipher, topSecretFingerprint = true)
                    }
                } else {
                    val access = SecretAccess(getApplication(), current)
                    val preparation = withContext(Dispatchers.IO) { access.prepareKek() }
                    pendingKek = preparation
                    _state.update { it.copy(busy = false, pendingCipher = preparation.cipher, topSecretFingerprint = false) }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "gate failed", t)
                _state.update { it.copy(busy = false, error = t.message) }
            }
        }
    }

    fun onBiometricAuthorized(cipher: Cipher) {
        val current = session ?: return
        val st = _state.value
        _state.update { it.copy(pendingCipher = null) }
        if (st.topSecretFingerprint) {
            viewModelScope.launch {
                try {
                    tsKekBio = withContext(Dispatchers.IO) {
                        TopSecretService(getApplication(), current.db).unlockTsKek(cipher)
                    }
                    _state.update { it.copy(awaitingSecondaryPassword = true) }
                } catch (t: Throwable) {
                    Log.e(TAG, "Top Secret fingerprint failed", t)
                    _state.update { it.copy(error = t.message) }
                }
            }
            return
        }
        val preparation = pendingKek ?: return
        if (preparation.mode == SecretAccess.KekMode.IMPORT) {
            _state.update { it.copy(awaitingMasterPassword = true, pendingCipher = cipher) }
            return
        }
        viewModelScope.launch {
            try {
                val unlocked = withContext(Dispatchers.IO) {
                    SecretAccess(getApplication(), current).completeKek(preparation, cipher)
                }
                kek?.clear()
                kek = unlocked
                pendingKek = null
                onKeyReady()
            } catch (t: Throwable) {
                Log.e(TAG, "KEK unlock failed", t)
                _state.update { it.copy(error = t.message, busy = false) }
            }
        }
    }

    fun onBiometricCancelled() {
        _state.update { it.copy(pendingCipher = null, busy = false) }
    }

    /** No enrolled fingerprint: Secret/Top-Secret tiers need one. */
    fun onBiometricUnavailable() {
        _state.update {
            it.copy(
                pendingCipher = null,
                busy = false,
                awaitingMasterPassword = false,
                awaitingSecondaryPassword = false,
                error = getApplication<Application>().getString(R.string.error_biometric_required),
            )
        }
    }

    /** Import fallback: the desktop KEK needs the master password. */
    fun submitMasterPassword(password: String) {
        val current = session ?: return
        val preparation = pendingKek ?: return
        val cipher = _state.value.pendingCipher ?: return
        val value = ProtectedValue.fromString(password)
        try {
            viewModelScope.launch {
                try {
                    val unlocked = withContext(Dispatchers.IO) {
                        SecretAccess(getApplication(), current).completeKek(preparation, cipher, value)
                    }
                    kek?.clear()
                    kek = unlocked
                    pendingKek = null
                    _state.update { it.copy(awaitingMasterPassword = false, pendingCipher = null) }
                    onKeyReady()
                } catch (t: Throwable) {
                    Log.e(TAG, "master password import failed", t)
                    _state.update { it.copy(error = t.message) }
                } finally {
                    value.clear()
                }
            }
        } catch (t: Throwable) {
            value.clear()
            throw t
        }
    }

    fun submitSecondaryPassword(password: String) {
        val current = session ?: return
        val bio = tsKekBio ?: return
        val value = ProtectedValue.fromString(password)
        _state.update { it.copy(awaitingSecondaryPassword = false, busy = true) }
        viewModelScope.launch {
            try {
                val unlocked = withContext(Dispatchers.IO) {
                    TopSecretService(getApplication(), current.db).unlockTopSecretKek(bio, value)
                }
                topSecretKek?.clear()
                topSecretKek = unlocked
                bio.clear()
                tsKekBio = null
                onKeyReady()
            } catch (t: Throwable) {
                Log.e(TAG, "secondary password failed", t)
                _state.update { it.copy(error = t.message, awaitingSecondaryPassword = true, busy = false) }
            } finally {
                value.clear()
            }
        }
    }

    private fun onKeyReady() {
        val seq = pendingSeq
        if (seq != null) {
            if (pendingRestore) {
                pendingRestore = false
                doRestore(seq)
            } else {
                loadDiff(seq)
            }
        }
        _state.update { it.copy(busy = false) }
    }

    // ---------------------------------------------------------------- diff

    private fun loadDiff(seq: Int) {
        val current = session ?: return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val (snapshotPayload, currentPayload, version) = withContext(Dispatchers.IO) {
                    val repo = ItemRepository(current.db, current.hfk)
                    val version = repo.version(_state.value.itemId, seq, kek, topSecretKek)
                    val item = repo.get(_state.value.itemId, kek, topSecretKek)
                    Triple(version.payload, item.payload, version)
                }
                val type = ItemType.fromId(_state.value.itemType)
                val before = if (type == ItemType.PASSWORD) TextDiff.prettyJson(snapshotPayload) else snapshotPayload
                val after = if (type == ItemType.PASSWORD) TextDiff.prettyJson(currentPayload) else currentPayload
                _state.update {
                    it.copy(
                        selectedVersion = version.summary,
                        selectedSeq = seq,
                        diff = TextDiff.diffLines(before, after),
                        busy = false,
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "loadDiff failed", t)
                _state.update { it.copy(busy = false, error = t.message) }
            }
        }
    }

    private fun doRestore(seq: Int) {
        val current = session ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ItemRepository(current.db, current.hfk)
                        .restoreVersion(_state.value.itemId, seq, kek, topSecretKek)
                }
                _state.update { it.copy(busy = false, restored = true, selectedSeq = null, diff = emptyList()) }
            } catch (t: Throwable) {
                Log.e(TAG, "restore failed", t)
                _state.update { it.copy(busy = false, error = t.message) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun cancelGate() {
        _state.update {
            it.copy(
                awaitingMasterPassword = false,
                awaitingSecondaryPassword = false,
                pendingCipher = null,
                busy = false,
            )
        }
    }

    fun consumeRestored() = _state.update { it.copy(restored = false) }

    fun clearKeys() {
        kek?.clear()
        topSecretKek?.clear()
        tsKekBio?.clear()
        kek = null
        topSecretKek = null
        tsKekBio = null
        pendingKek = null
    }

    override fun onCleared() {
        clearKeys()
    }

    private companion object {
        const val TAG = "cwl:History"
    }
}
