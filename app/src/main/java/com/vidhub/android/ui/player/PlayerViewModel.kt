package com.vidhub.android.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vidhub.android.data.repository.VideoRepository
import com.vidhub.android.model.VideoItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: VideoRepository,
) : ViewModel() {

    private var item: VideoItem? = null
    private var episodeCount: Int = 0

    /** 当前集下标，由 Activity 在切集时更新 */
    var episodeIndex: Int = 0

    fun bind(video: VideoItem, count: Int, startIndex: Int) {
        if (item != null) return
        item = video
        episodeCount = count
        episodeIndex = startIndex
    }

    /** 保存当前播放进度（接近结尾自动归零，由 repository 处理） */
    fun saveProgress(positionMs: Long, durationMs: Long) {
        val video = item ?: return
        if (positionMs < 0) return
        viewModelScope.launch {
            repository.saveProgress(video, episodeIndex, episodeCount, positionMs, durationMs)
        }
    }

    /** 某集自然播完：把下一集记为"当前看到"（位置 0） */
    fun saveEpisodeFinished() {
        val video = item ?: return
        viewModelScope.launch {
            repository.saveProgress(video, episodeIndex, episodeCount, 0L, 0L)
        }
    }
}
