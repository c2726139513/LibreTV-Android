package com.vidhub.android.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vidhub.android.data.repository.VideoRepository
import com.vidhub.android.model.VideoItem
import com.vidhub.android.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: VideoRepository,
) : ViewModel() {

    data class SearchUiState(
        val results: List<VideoItem> = emptyList(),
        val searching: Boolean = false,
        /** 已发起过至少一次搜索（用于区分"未搜索"与"无结果"） */
        val hasSearched: Boolean = false,
        val noServer: Boolean = false,
        val authFailed: Boolean = false,
        /** 请求失败的源数量（部分失败不影响结果展示） */
        val failedSourceCount: Int = 0,
    )

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            queryFlow
                .debounce { query -> if (query.isBlank()) 0L else Constants.SEARCH_DEBOUNCE_MS }
                .distinctUntilChanged()
                .collectLatest { query -> performSearch(query.trim()) }
        }
    }

    fun onQueryChanged(query: String) {
        queryFlow.value = query
    }

    fun onQuerySubmit(query: String) {
        queryFlow.value = query
    }

    private suspend fun performSearch(query: String) {
        if (query.isBlank()) {
            _uiState.value = SearchUiState()
            return
        }
        val server = repository.getActiveServerSnapshot()
        if (server == null) {
            _uiState.update { it.copy(searching = false, hasSearched = true, noServer = true, results = emptyList()) }
            return
        }

        _uiState.update {
            it.copy(
                results = emptyList(),
                searching = true,
                hasSearched = true,
                noServer = false,
                authFailed = false,
                failedSourceCount = 0,
            )
        }

        // 多源聚合：按 stableKey 去重，结果随源返回渐进更新
        val dedup = LinkedHashMap<String, VideoItem>()
        var failed = 0
        repository.search(server, query).collect { event ->
            when (event) {
                is VideoRepository.SearchEvent.SourceResult -> {
                    event.items.forEach { item -> dedup.putIfAbsent(item.stableKey, item) }
                    _uiState.update { it.copy(results = dedup.values.toList()) }
                }
                is VideoRepository.SearchEvent.SourceFailed -> {
                    failed++
                    _uiState.update { it.copy(failedSourceCount = failed) }
                }
                VideoRepository.SearchEvent.AuthFailed -> {
                    _uiState.update { it.copy(authFailed = true, searching = false) }
                    return@collect
                }
            }
        }
        _uiState.update { it.copy(searching = false) }
    }
}
