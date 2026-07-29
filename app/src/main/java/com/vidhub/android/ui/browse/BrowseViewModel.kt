package com.vidhub.android.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vidhub.android.data.local.ServerConfigStore
import com.vidhub.android.data.repository.VideoRepository
import com.vidhub.android.model.FavoriteItem
import com.vidhub.android.model.ServerConfig
import com.vidhub.android.model.VideoItem
import com.vidhub.android.model.WatchHistoryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val serverConfigStore: ServerConfigStore,
) : ViewModel() {

    data class BrowseState(
        val servers: List<ServerConfig> = emptyList(),
        val activeServerId: String? = null,
        val history: List<WatchHistoryItem> = emptyList(),
        val favorites: List<FavoriteItem> = emptyList(),
    )

    val state: StateFlow<BrowseState> = combine(
        serverConfigStore.servers,
        serverConfigStore.activeServerId,
        repository.history,
        repository.favorites,
    ) { servers, activeId, history, favorites ->
        BrowseState(servers, activeId, history, favorites)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseState())

    fun setActiveServer(id: String) = serverConfigStore.setActiveServer(id)

    // ---------- 继续观看：取剧集后跳转播放 ----------

    sealed interface ResumeEvent {
        data class Open(
            val item: VideoItem,
            val episodeUrls: List<String>,
            val episodeIndex: Int,
            val startPositionMs: Long,
        ) : ResumeEvent

        data class Failed(val reason: String) : ResumeEvent
    }

    private val _resumeEvents = MutableSharedFlow<ResumeEvent>()
    val resumeEvents: SharedFlow<ResumeEvent> = _resumeEvents

    fun resume(historyItem: WatchHistoryItem) {
        viewModelScope.launch {
            val server = serverConfigStore.getServer(historyItem.serverId)
                ?: serverConfigStore.getActiveServerSnapshot()
            if (server == null) {
                _resumeEvents.emit(ResumeEvent.Failed("服务器不存在，请重新配置"))
                return@launch
            }
            val videoItem = VideoItem(
                vodId = historyItem.vodId,
                title = historyItem.title,
                coverUrl = historyItem.coverUrl,
                sourceApi = historyItem.sourceApi,
                serverId = server.id,
            )
            try {
                val detail = repository.detail(server, videoItem)
                if (detail.episodes.isEmpty()) {
                    _resumeEvents.emit(ResumeEvent.Failed("未获取到剧集"))
                    return@launch
                }
                _resumeEvents.emit(
                    ResumeEvent.Open(
                        item = videoItem,
                        episodeUrls = detail.episodes.map { it.url },
                        episodeIndex = historyItem.episodeIndex.coerceIn(0, detail.episodes.size - 1),
                        startPositionMs = historyItem.positionMs,
                    )
                )
            } catch (e: Exception) {
                _resumeEvents.emit(ResumeEvent.Failed(e.message ?: "加载失败"))
            }
        }
    }
}
