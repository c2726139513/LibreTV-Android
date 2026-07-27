package com.vidhub.android.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vidhub.android.data.repository.VideoRepository
import com.vidhub.android.model.VideoItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<VideoItem>>(emptyList())
    val searchResults: StateFlow<List<VideoItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var searchJob: Job? = null
    private var currentPage = 1
    private var currentQuery = ""

    fun search(query: String) {
        currentQuery = query
        searchJob?.cancel()

        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _error.value = null
            currentPage = 1
            _searchResults.value = mutableListOf()

            val server = repository.getActiveServerSync()
            if (server == null) {
                _error.value = "请先在设置中添加服务器"
                _isSearching.value = false
                return@launch
            }

            repository.search(server, query, currentPage)
                .onSuccess { results ->
                    _searchResults.value = results
                    if (results.isEmpty()) {
                        _error.value = "没有找到结果，请尝试其他关键词"
                    }
                }
                .onFailure { e ->
                    _error.value = e.message ?: "搜索失败"
                }
            _isSearching.value = false
        }
    }

    fun loadMore() {
        if (_isSearching.value || currentQuery.isBlank()) return

        viewModelScope.launch {
            _isSearching.value = true
            currentPage++

            val server = repository.getActiveServerSync() ?: return@launch
            repository.search(server, currentQuery, currentPage)
                .onSuccess { results ->
                    _searchResults.value = _searchResults.value + results
                }
                .onFailure {
                    currentPage--
                }
            _isSearching.value = false
        }
    }
}
