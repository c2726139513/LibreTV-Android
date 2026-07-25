package com.libretv.android.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.libretv.android.data.repository.VideoRepository
import com.libretv.android.model.ServerConfig
import com.libretv.android.util.Sha256
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: VideoRepository
) : androidx.lifecycle.ViewModel() {

    private val _serverConfig = MutableStateFlow<ServerConfig?>(null)
    val serverConfig: StateFlow<ServerConfig?> = _serverConfig.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadConfig()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            repository.getActiveServer().collect { config ->
                _serverConfig.value = config
                if (_isLoading.value) {
                    _isLoading.value = false
                }
            }
        }
    }

    fun savePlaybackProgress(videoId: String, title: String, coverUrl: String?,
                              episodeIndex: Int, episodeName: String?,
                              position: Long, duration: Long) {
        viewModelScope.launch {
            val server = _serverConfig.value
            val item = com.libretv.android.data.local.WatchHistoryItem(
                videoId = videoId,
                title = title,
                coverUrl = coverUrl,
                serverId = server?.id ?: "",
                episodeIndex = episodeIndex,
                episodeName = episodeName,
                position = position,
                duration = duration,
                lastWatched = System.currentTimeMillis(),
                sourceName = server?.name
            )
            repository.saveWatchProgress(item)
        }
    }

    fun getAuthParams(): Pair<String, String>? {
        val config = repository.getActiveServerSync() ?: return null
        val hash = Sha256.hash(config.password)
        val timestamp = System.currentTimeMillis().toString()
        return Pair(hash, timestamp)
    }
}
