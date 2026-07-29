package com.vidhub.android.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vidhub.android.data.local.ServerConfigStore
import com.vidhub.android.data.repository.VideoRepository
import com.vidhub.android.model.Episode
import com.vidhub.android.model.VideoItem
import com.vidhub.android.model.WatchHistoryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val serverConfigStore: ServerConfigStore,
) : ViewModel() {

    data class DetailUiState(
        val item: VideoItem? = null,
        val episodes: List<Episode> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        val history: WatchHistoryItem? = null,
        val isFavorite: Boolean = false,
        /** 播放按钮文案：有进度时为"续播 第N集" */
        val playLabel: String = "播放",
    )

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun load(item: VideoItem) {
        if (_uiState.value.item != null) return
        _uiState.update { it.copy(item = item) }

        // 收藏状态：持续观察本地存储
        viewModelScope.launch {
            repository.favorites.collect { list ->
                _uiState.update { state ->
                    state.copy(isFavorite = list.any { it.key == item.stableKey })
                }
            }
        }

        // 播放进度：持续观察（播放返回后自动刷新"续播"文案）
        viewModelScope.launch {
            repository.history.collect { list ->
                val history = list.firstOrNull { it.key == item.stableKey }
                _uiState.update { state ->
                    state.copy(
                        history = history,
                        playLabel = if (history != null && history.positionMs > 0L) {
                            "续播 第${history.episodeIndex + 1}集"
                        } else {
                            "播放"
                        },
                    )
                }
            }
        }

        // 详情与剧集
        viewModelScope.launch {
            val server = serverConfigStore.getServer(item.serverId)
                ?: serverConfigStore.getActiveServerSnapshot()
            if (server == null) {
                _uiState.update { it.copy(loading = false, error = "服务器不存在，请重新配置") }
                return@launch
            }
            try {
                val detail = repository.detail(server, item)
                val info = detail.info
                _uiState.update { state ->
                    val current = state.item ?: item
                    val merged = current.copy(
                        title = info?.title?.takeIf { t -> t.isNotBlank() } ?: current.title,
                        coverUrl = info?.cover?.takeIf { c -> c.isNotBlank() } ?: current.coverUrl,
                        description = info?.desc?.takeIf { d -> d.isNotBlank() } ?: current.description,
                        typeName = info?.type ?: current.typeName,
                        year = info?.year ?: current.year,
                        area = info?.area ?: current.area,
                        director = info?.director ?: current.director,
                        actor = info?.actor ?: current.actor,
                        remarks = info?.remarks ?: current.remarks,
                    )
                    state.copy(
                        item = merged,
                        episodes = detail.episodes,
                        loading = false,
                        error = if (detail.episodes.isEmpty()) "暂无可播放剧集" else null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    fun toggleFavorite() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch { repository.toggleFavorite(item) }
    }
}
