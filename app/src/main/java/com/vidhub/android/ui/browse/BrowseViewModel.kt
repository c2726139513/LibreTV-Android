package com.vidhub.android.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vidhub.android.data.repository.VideoRepository
import com.vidhub.android.data.local.WatchHistoryItem
import com.vidhub.android.model.ServerConfig
import com.vidhub.android.model.VideoItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {

    private val _servers = MutableStateFlow<List<ServerConfig>>(emptyList())
    val servers: StateFlow<List<ServerConfig>> = _servers.asStateFlow()

    private val _activeServer = MutableStateFlow<ServerConfig?>(null)
    val activeServer: StateFlow<ServerConfig?> = _activeServer.asStateFlow()

    private val _continueWatching = MutableStateFlow<List<WatchHistoryItem>>(emptyList())
    val continueWatching: StateFlow<List<WatchHistoryItem>> = _continueWatching.asStateFlow()

    private val _favorites = MutableStateFlow<List<VideoItem>>(emptyList())
    val favorites: StateFlow<List<VideoItem>> = _favorites.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        _isLoading.value = true
        viewModelScope.launch {
            repository.getServers().catch { e ->
                _servers.value = emptyList()
            }.collect { serverList ->
                _servers.value = serverList
            }
        }
        viewModelScope.launch {
            repository.getActiveServer().catch { }.collect { server ->
                _activeServer.value = server
            }
        }
        viewModelScope.launch {
            repository.getWatchHistory().catch { }.collect { history ->
                _continueWatching.value = history
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        loadData()
    }
}
