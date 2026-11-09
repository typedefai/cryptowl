package com.typedefai.cryptowl

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.typedefai.cryptowl.model.ItemFilter
import com.typedefai.cryptowl.model.ItemSummary
import com.typedefai.cryptowl.vault.ItemRepository
import com.typedefai.cryptowl.vault.VaultSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Item list state: all items, filtered by type and title search. */
class ItemsViewModel(app: Application) : AndroidViewModel(app) {

    @Volatile
    private var session: VaultSession? = null

    private val _allItems = MutableStateFlow<List<ItemSummary>>(emptyList())
    private val _filter = MutableStateFlow(ItemFilter.ALL)
    val filter: StateFlow<ItemFilter> = _filter.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val items: StateFlow<List<ItemSummary>> =
        combine(_allItems, _filter, _query) { items, filter, query ->
            items.filter { item ->
                (filter.typeId == null || item.type == filter.typeId) &&
                    (query.isBlank() || item.title.contains(query, ignoreCase = true))
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun attach(newSession: VaultSession) {
        if (session !== newSession) {
            session = newSession
            refresh()
        }
    }

    fun detach() {
        session = null
        _allItems.value = emptyList()
    }

    fun setFilter(value: ItemFilter) {
        _filter.value = value
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun clearError() {
        _error.value = null
    }

    fun refresh() {
        val current = session ?: return
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    ItemRepository(current.db, current.hfk).list()
                }
                _allItems.value = list
            } catch (t: Throwable) {
                Log.e(TAG, "refresh failed", t)
                _error.value = t.message ?: "could not load items"
            }
        }
    }

    fun delete(id: String) {
        val current = session ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ItemRepository(current.db, current.hfk).softDelete(id)
                }
                refresh()
            } catch (t: Throwable) {
                Log.e(TAG, "delete failed", t)
                _error.value = t.message ?: "could not delete the item"
            }
        }
    }

    private companion object {
        const val TAG = "cwl:ItemsViewModel"
    }
}
