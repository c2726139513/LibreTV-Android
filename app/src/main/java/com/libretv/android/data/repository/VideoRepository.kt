package com.libretv.android.data.repository

import com.libretv.android.data.local.ServerConfigStore
import com.libretv.android.data.local.WatchHistoryItem
import com.libretv.android.data.local.WatchHistoryStore
import com.libretv.android.data.remote.VidHubApi
import com.libretv.android.data.remote.dto.toVideoItem
import com.libretv.android.model.Episode
import com.libretv.android.model.ServerConfig
import com.libretv.android.model.VideoItem
import com.libretv.android.util.Sha256
import kotlinx.coroutines.flow.Flow
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    private val api: VidHubApi,
    private val serverConfigStore: ServerConfigStore,
    private val watchHistoryStore: WatchHistoryStore
) {
    fun getServers(): Flow<List<ServerConfig>> = serverConfigStore.getServers()

    fun getActiveServer(): Flow<ServerConfig?> = serverConfigStore.getActiveServer()

    fun getActiveServerSync(): ServerConfig? = serverConfigStore.getActiveServerSync()

    suspend fun addServer(config: ServerConfig) = serverConfigStore.addServer(config)

    suspend fun updateServer(config: ServerConfig) = serverConfigStore.updateServer(config)

    suspend fun removeServer(id: String) = serverConfigStore.removeServer(id)

    suspend fun setActiveServer(id: String) = serverConfigStore.setActiveServer(id)

    suspend fun search(server: ServerConfig, keyword: String, page: Int = 1): Result<List<VideoItem>> {
        return try {
            val cmsSources = server.cmsSources.ifEmpty {
                return@search Result.success(emptyList())
            }
            val results = cmsSources.mapNotNull { cmsUrl ->
                val params = mutableMapOf(
                    "wd" to keyword,
                    "pg" to page.toString()
                )
                if (cmsUrl.isNotBlank()) {
                    params["apiUrl"] = cmsUrl
                }
                val apiUrl = buildVidHubUrl(server, "/api/search", params)
                val response = api.search(apiUrl)
                if (response.code == 200) {
                    response.list?.map { it.toVideoItem() } ?: emptyList()
                } else emptyList()
            }
            Result.success(results.flatten())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun detail(server: ServerConfig, vodId: String, apiUrl: String? = null): Result<VideoItem?> {
        return try {
            val params = mutableMapOf("id" to vodId)
            if (!apiUrl.isNullOrBlank()) {
                params["apiUrl"] = apiUrl
            }
            val url = buildVidHubUrl(server, "/api/detail", params)
            val response = api.detail(url)
            if (response.code == 200 && response.videoInfo != null) {
                val info = response.videoInfo
                val episodes = response.episodes?.mapIndexed { index, url ->
                    Episode(name = "第${index + 1}集", url = url, index = index)
                } ?: emptyList()
                val videoItem = VideoItem(
                    vodId = vodId,
                    title = info.title ?: "",
                    coverUrl = info.cover,
                    remarks = info.remarks,
                    year = info.year,
                    area = info.area,
                    director = info.director,
                    actor = info.actor,
                    typeName = info.type,
                    description = info.desc,
                    playFrom = info.sourceName
                )
                Result.success(videoItem.copy(episodes = episodes))
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getWatchHistory(limit: Int = 20): Flow<List<WatchHistoryItem>> =
        watchHistoryStore.getRecentHistory(limit)

    suspend fun saveWatchProgress(item: WatchHistoryItem) =
        watchHistoryStore.saveProgress(item)

    suspend fun clearWatchHistory() = watchHistoryStore.clearAll()

    fun buildVidHubUrl(server: ServerConfig, path: String, params: Map<String, String>): String {
        val base = server.url.trimEnd('/')
        val timestamp = System.currentTimeMillis()
        val hash = Sha256.hash(server.password)
        val queryString = params.entries.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        return "$base$path?$queryString&auth=$hash&t=$timestamp"
    }
}
