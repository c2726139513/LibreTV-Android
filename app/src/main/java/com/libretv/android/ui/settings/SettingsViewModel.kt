package com.libretv.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libretv.android.data.repository.VideoRepository
import com.libretv.android.model.ServerConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {

    private val _servers = MutableStateFlow<List<ServerConfig>>(emptyList())
    val servers: StateFlow<List<ServerConfig>> = _servers.asStateFlow()

    private val _activeServerId = MutableStateFlow<String?>(null)
    val activeServerId: StateFlow<String?> = _activeServerId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadServers()
    }

    fun loadServers() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getServers().catch { }.collect { serverList ->
                _servers.value = serverList
                _activeServerId.value = serverList.find { it.isActive }?.id
            }
            _isLoading.value = false
        }
    }

    fun addServer(name: String, url: String, password: String, cmsSources: List<String> = emptyList()) {
        viewModelScope.launch {
            val config = ServerConfig(
                name = name,
                url = url,
                password = password,
                isActive = _servers.value.isEmpty(),
                cmsSources = cmsSources
            )
            repository.addServer(config)
            loadServers()
        }
    }

    fun updateServer(id: String, name: String, url: String, password: String, cmsSources: List<String>) {
        viewModelScope.launch {
            val config = ServerConfig(
                id = id,
                name = name,
                url = url,
                password = password,
                isActive = _servers.value.find { it.id == id }?.isActive ?: false,
                cmsSources = cmsSources,
                addedAt = _servers.value.find { it.id == id }?.addedAt ?: System.currentTimeMillis()
            )
            repository.updateServer(config)
            loadServers()
        }
    }

    fun removeServer(id: String) {
        viewModelScope.launch {
            repository.removeServer(id)
            loadServers()
        }
    }

    fun setActiveServer(id: String) {
        viewModelScope.launch {
            repository.setActiveServer(id)
            loadServers()
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            repository.clearWatchHistory()
        }
    }
}
