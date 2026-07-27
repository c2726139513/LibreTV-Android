package com.vidhub.android.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vidhub.android.data.repository.VideoRepository
import com.vidhub.android.data.remote.dto.SourceInfo
import com.vidhub.android.model.ServerConfig
import com.vidhub.android.model.VideoItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {

    private val _video = MutableStateFlow<VideoItem?>(null)
    val video: StateFlow<VideoItem?> = _video.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedEpisode = MutableStateFlow<Int>(0)
    val selectedEpisode: StateFlow<Int> = _selectedEpisode.asStateFlow()

    fun loadDetail(vodId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val server = repository.getActiveServerSync()
            if (server == null) {
                _error.value = "请先配置服务器"
                _isLoading.value = false
                return@launch
            }

            val apiUrl = resolveFirstSourceUrl(server)
            repository.detail(server, vodId, apiUrl)
                .onSuccess { videoItem ->
                    _video.value = videoItem
                }
                .onFailure { e ->
                    _error.value = e.message ?: "加载详情失败"
                }
            _isLoading.value = false
        }
    }

    fun selectEpisode(index: Int) {
        _selectedEpisode.value = index
    }

    private fun resolveFirstSourceUrl(server: ServerConfig): String? {
        val sources = repository.getCachedSources(server.url.trimEnd('/'))
        server.enabledSources.firstOrNull()?.let { key ->
            sources.find { it.key == key }?.let { return it.api }
        }
        return server.customSources.firstOrNull()?.url
    }

    fun getCurrentEpisodeUrl(): String? {
        val video = _video.value ?: return null
        val episode = video.episodes.getOrNull(_selectedEpisode.value) ?: return null
        return episode.url
    }
}
