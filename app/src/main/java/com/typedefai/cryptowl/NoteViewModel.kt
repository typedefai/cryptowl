package com.typedefai.cryptowl

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.typedefai.cryptowl.vault.Note
import com.typedefai.cryptowl.vault.NoteDraft
import com.typedefai.cryptowl.vault.NoteRepository
import com.typedefai.cryptowl.vault.NoteSummary
import com.typedefai.cryptowl.vault.VaultSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Confidential-tier note CRUD. No fingerprint: L1 data is readable once the
 * vault is unlocked, and SQLCipher is the at-rest boundary (docs/design.md).
 */
class NoteViewModel(app: Application) : AndroidViewModel(app) {

    @Volatile
    private var session: VaultSession? = null

    private val _entries = MutableStateFlow<List<NoteSummary>>(emptyList())
    val entries: StateFlow<List<NoteSummary>> = _entries.asStateFlow()

    private val _detail = MutableStateFlow<Note?>(null)
    val detail: StateFlow<Note?> = _detail.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun attach(newSession: VaultSession) {
        if (session !== newSession) {
            session = newSession
            _detail.value = null
            refresh()
        }
    }

    fun detach() {
        session = null
        _entries.value = emptyList()
        _detail.value = null
    }

    fun refresh() {
        val db = session?.db ?: return
        viewModelScope.launch {
            try {
                _entries.value = withContext(Dispatchers.IO) { NoteRepository(db).list() }
            } catch (e: Throwable) {
                Log.e(TAG, "refresh failed", e)
                _error.value = e.message ?: getApplication<Application>().getString(R.string.notes_error_operation)
            }
        }
    }

    fun load(id: String) {
        val db = session?.db ?: return
        viewModelScope.launch {
            try {
                _detail.value = withContext(Dispatchers.IO) { NoteRepository(db).get(id) }
            } catch (e: Throwable) {
                Log.e(TAG, "load failed", e)
                _error.value = e.message ?: getApplication<Application>().getString(R.string.notes_error_operation)
            }
        }
    }

    fun save(draft: NoteDraft) {
        val db = session?.db ?: return
        _error.value = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val repo = NoteRepository(db)
                    if (draft.id == null) {
                        repo.create(draft)
                    } else {
                        repo.update(draft.id, draft)
                        _detail.value = repo.get(draft.id)
                    }
                }
                _saved.value = true
            } catch (e: Throwable) {
                Log.e(TAG, "save failed", e)
                _error.value = e.message ?: getApplication<Application>().getString(R.string.notes_error_operation)
            } finally {
                refresh()
            }
        }
    }

    fun togglePin(id: String, pinned: Boolean) {
        val db = session?.db ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { NoteRepository(db).setPinned(id, pinned) }
            } catch (e: Throwable) {
                Log.e(TAG, "togglePin failed", e)
                _error.value = e.message ?: getApplication<Application>().getString(R.string.notes_error_operation)
            } finally {
                refresh()
                _detail.value = _detail.value?.copy(pinned = pinned)
            }
        }
    }

    fun delete(id: String) {
        val db = session?.db ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { NoteRepository(db).softDelete(id) }
                _detail.value = null
            } catch (e: Throwable) {
                Log.e(TAG, "delete failed", e)
                _error.value = e.message ?: getApplication<Application>().getString(R.string.notes_error_operation)
            } finally {
                refresh()
            }
        }
    }

    fun clearDetail() {
        _detail.value = null
    }

    fun consumeSaved() {
        _saved.value = false
    }

    fun clearError() {
        _error.value = null
    }

    private companion object {
        const val TAG = "cwl:NoteViewModel"
    }
}
