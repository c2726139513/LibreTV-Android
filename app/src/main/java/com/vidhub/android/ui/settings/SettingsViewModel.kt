package com.vidhub.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vidhub.android.data.local.ServerConfigStore
import com.vidhub.android.data.local.SourcesCacheStore
import com.vidhub.android.data.repository.VideoRepository
import com.vidhub.android.model.ServerConfig
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
class SettingsViewModel @Inject constructor(
    private val serverConfigStore: ServerConfigStore,
    private val sourcesCacheStore: SourcesCacheStore,
    private val repository: VideoRepository,
) : ViewModel() {

    data class SettingsState(
        val servers: List<ServerConfig> = emptyList(),
        val activeServerId: String? = null,
    )

    val state: StateFlow<SettingsState> = combine(
        serverConfigStore.servers,
        serverConfigStore.activeServerId,
    ) { servers, activeId -> SettingsState(servers, activeId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    sealed interface Event {
        data class Message(val text: String) : Event
    }

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events

    fun setActive(server: ServerConfig) {
        serverConfigStore.setActiveServer(server.id)
        viewModelScope.launch { _events.emit(Event.Message("已切换到「${server.name}」")) }
    }

    fun delete(server: ServerConfig) {
        viewModelScope.launch {
            serverConfigStore.removeServer(server.id)
            sourcesCacheStore.remove(server.id)
            _events.emit(Event.Message("已删除「${server.name}」"))
        }
    }

    /** 从服务端拉取最新数据源列表并缓存 */
    fun refreshSources(server: ServerConfig) {
        viewModelScope.launch {
            try {
                val sources = repository.refreshSources(server)
                _events.emit(Event.Message("「${server.name}」数据源已更新：${sources.size} 个"))
            } catch (e: Exception) {
                _events.emit(Event.Message("刷新失败：${e.message ?: "未知错误"}"))
            }
        }
    }
}
