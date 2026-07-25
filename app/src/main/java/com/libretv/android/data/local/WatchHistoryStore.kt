package com.libretv.android.data.local

import android.content.Context
import com.libretv.android.model.VideoItem
import com.libretv.android.util.Constants
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.lang.reflect.ParameterizedType

data class WatchHistoryItem(
    val videoId: String,
    val title: String,
    val coverUrl: String?,
    val serverId: String,
    val episodeIndex: Int,
    val episodeName: String?,
    val position: Long,
    val duration: Long,
    val lastWatched: Long,
    val sourceName: String?
)

class WatchHistoryStore(private val context: Context) {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val listType = object : ParameterizedType {
        override fun getRawType() = List::class.java
        override fun getActualTypeArguments() = arrayOf(WatchHistoryItem::class.java)
        override fun getOwnerType() = null
    }
    private val adapter = moshi.adapter<List<WatchHistoryItem>>(listType)
    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    fun getRecentHistory(limit: Int = 20): Flow<List<WatchHistoryItem>> = flow {
        val items = getHistoryList()
        emit(items.sortedByDescending { it.lastWatched }.take(limit))
    }.flowOn(Dispatchers.IO)

    suspend fun saveProgress(item: WatchHistoryItem) = withContext(Dispatchers.IO) {
        val items = getHistoryList().toMutableList()
        val existingIndex = items.indexOfFirst { it.videoId == item.videoId && it.episodeIndex == item.episodeIndex }
        if (existingIndex >= 0) {
            items[existingIndex] = item
        } else {
            items.add(item)
        }
        prefs.edit().putString(Constants.WATCH_HISTORY_KEY, adapter.toJson(items)).apply()
    }

    suspend fun deleteItem(videoId: String) = withContext(Dispatchers.IO) {
        val items = getHistoryList().filter { it.videoId != videoId }
        prefs.edit().putString(Constants.WATCH_HISTORY_KEY, adapter.toJson(items)).apply()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        prefs.edit().remove(Constants.WATCH_HISTORY_KEY).apply()
    }

    private fun getHistoryList(): List<WatchHistoryItem> {
        val json = prefs.getString(Constants.WATCH_HISTORY_KEY, null) ?: return emptyList()
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
