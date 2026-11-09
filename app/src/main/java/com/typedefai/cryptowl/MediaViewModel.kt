package com.typedefai.cryptowl

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.typedefai.cryptowl.vault.MediaItem
import com.typedefai.cryptowl.vault.MediaService
import com.typedefai.cryptowl.vault.VaultSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Confidential-tier media vault: import (images in memory, videos streamed),
 * list and delete. No fingerprint — C-tier files share the vault FEK and are
 * readable once the vault is unlocked.
 */
class MediaViewModel(app: Application) : AndroidViewModel(app) {

    @Volatile
    private var session: VaultSession? = null

    private val _entries = MutableStateFlow<List<MediaItem>>(emptyList())
    val entries: StateFlow<List<MediaItem>> = _entries.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun attach(newSession: VaultSession) {
        if (session !== newSession) {
            session = newSession
            refresh()
        }
    }

    fun detach() {
        session = null
        _entries.value = emptyList()
    }

    fun refresh() {
        val openSession = session ?: return
        viewModelScope.launch {
            try {
                _entries.value = withContext(Dispatchers.IO) {
                    MediaService(getApplication(), openSession).list(limit = 1000).items
                }
            } catch (e: Throwable) {
                Log.e(TAG, "refresh failed", e)
                _error.value = message(e)
            }
        }
    }

    /** Imports picked media; images are read in memory, other files stream. */
    fun import(uris: List<Uri>) {
        val openSession = session ?: return
        if (uris.isEmpty()) return
        _error.value = null
        _importing.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val service = MediaService(getApplication(), openSession)
                    for (uri in uris) {
                        importOne(service, uri)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "import failed", e)
                _error.value = message(e)
            } finally {
                _importing.value = false
                refresh()
            }
        }
    }

    fun delete(id: String) {
        val openSession = session ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    MediaService(getApplication(), openSession).delete(id)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "delete failed", e)
                _error.value = message(e)
            } finally {
                refresh()
            }
        }
    }

    /** Decrypted original for the viewer (images). */
    suspend fun loadOriginal(item: MediaItem): Bitmap? {
        val openSession = session ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val bytes = MediaService(getApplication(), openSession).originalBytes(item)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Throwable) {
                Log.e(TAG, "loadOriginal failed", e)
                null
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun importOne(service: MediaService, uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        var name: String? = null
        var size = 0L
        resolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !c.isNull(nameIndex)) name = c.getString(nameIndex)
                val sizeIndex = c.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !c.isNull(sizeIndex)) size = c.getLong(sizeIndex)
            }
        }
        val mime = resolver.getType(uri)
        if (mime?.startsWith("image/") == true) {
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("cannot open $name")
            service.importImage(bytes, name, mime)
        } else {
            service.importFile(uri, name, mime, size)
        }
    }

    private fun message(e: Throwable): String =
        e.message ?: getApplication<Application>().getString(R.string.media_error)

    private companion object {
        const val TAG = "cwl:MediaViewModel"
    }
}
