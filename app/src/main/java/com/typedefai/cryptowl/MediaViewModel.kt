package com.typedefai.cryptowl

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.typedefai.cryptowl.vault.MediaEntry
import com.typedefai.cryptowl.vault.MediaService
import com.typedefai.cryptowl.vault.VaultSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A media item with its decrypted thumbnail (C tier — no biometric gate). */
data class MediaUiEntry(
    val entry: MediaEntry,
    val thumbnail: ImageBitmap?,
)

class MediaViewModel(app: Application) : AndroidViewModel(app) {

    @Volatile
    private var session: VaultSession? = null

    private val _entries = MutableStateFlow<List<MediaUiEntry>>(emptyList())
    val entries: StateFlow<List<MediaUiEntry>> = _entries.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

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
        val current = session ?: return
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    val service = MediaService(getApplication(), current)
                    service.list().map { entry ->
                        val bytes = runCatching { service.thumbnail(entry) }.getOrNull()
                        val bitmap = bytes?.let {
                            runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull()
                        }
                        MediaUiEntry(entry, bitmap?.asImageBitmap())
                    }
                }
                _entries.value = list
            } catch (t: Throwable) {
                Log.e(TAG, "refresh failed", t)
                _error.value = t.message
            }
        }
    }

    fun importImage(bytes: ByteArray, name: String, mimeType: String?) {
        val current = session ?: return
        _busy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    MediaService(getApplication(), current).importImage(bytes, name, mimeType)
                }
                refresh()
            } catch (t: Throwable) {
                Log.e(TAG, "importImage failed", t)
                _error.value = t.message
            } finally {
                _busy.value = false
            }
        }
    }

    fun importUri(uri: Uri, name: String, mimeType: String?, isVideo: Boolean) {
        val current = session ?: return
        _busy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    MediaService(getApplication(), current).importUri(uri, name, mimeType, isVideo)
                }
                refresh()
            } catch (t: Throwable) {
                Log.e(TAG, "importUri failed", t)
                _error.value = t.message
            } finally {
                _busy.value = false
            }
        }
    }

    /** Decrypts the original for the viewer; null on failure. */
    fun originalBytes(entry: MediaEntry): ByteArray? {
        val current = session ?: return null
        return try {
            MediaService(getApplication(), current).original(entry)
        } catch (t: Throwable) {
            Log.e(TAG, "originalBytes failed", t)
            _error.value = t.message
            null
        }
    }

    /** Decrypts the original into an app-private cache file (video playback). */
    fun decryptToCache(entry: MediaEntry): java.io.File? {
        val current = session ?: return null
        return try {
            val target = java.io.File(
                getApplication<Application>().cacheDir,
                "cwl_play_${java.util.UUID.randomUUID()}.bin",
            )
            MediaService(getApplication(), current).decryptToFile(entry, target)
            target
        } catch (t: Throwable) {
            Log.e(TAG, "decryptToCache failed", t)
            _error.value = t.message
            null
        }
    }

    fun delete(entry: MediaEntry) {
        val current = session ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    MediaService(getApplication(), current).delete(entry)
                }
                refresh()
            } catch (t: Throwable) {
                Log.e(TAG, "delete failed", t)
                _error.value = t.message
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private companion object {
        const val TAG = "cwl:MediaViewModel"
    }
}
