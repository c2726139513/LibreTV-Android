package com.libretv.android.data.repository

import com.libretv.android.data.local.ServerConfigStore
import com.libretv.android.data.local.WatchHistoryItem
import com.libretv.android.data.local.WatchHistoryStore
import com.libretv.android.data.remote.LibreTVApi
import com.libretv.android.data.remote.dto.toVideoItem
import com.libretv.android.model.ServerConfig
import com.libretv.android.model.VideoItem
import com.libretv.android.util.Sha256
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    private val api: LibreTVApi,
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
            val results = server.cmsSources.ifEmpty { listOf("") }.mapNotNull { cmsSource ->
                val cmsUrl = if (cmsSource.isBlank()) {
                    buildSearchUrl(keyword, page)
                } else {
                    "${cmsSource}?ac=videolist&wd=$keyword&pg=$page"
                }
                val proxyUrl = buildProxyUrl(server, cmsUrl)
                val response = api.search(proxyUrl)
                if (response.code == 1) {
                    response.list?.map { it.toVideoItem() } ?: emptyList()
                } else emptyList()
            }
            Result.success(results.flatten())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun detail(server: ServerConfig, vodId: String): Result<VideoItem?> {
        return try {
            val cmsUrl = "?ac=videolist&ids=$vodId"
            val proxyUrl = buildProxyUrl(server, cmsUrl)
            val response = api.detail(proxyUrl)
            if (response.code == 1) {
                Result.success(response.list?.firstOrNull()?.toVideoItem())
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

    private fun buildProxyUrl(server: ServerConfig, targetUrl: String): String {
        val encoded = java.net.URLEncoder.encode(targetUrl, "UTF-8")
        val timestamp = System.currentTimeMillis()
        val hash = Sha256.hash(server.password)
        val base = server.url.trimEnd('/')
        return "$base/proxy/$encoded?auth=$hash&t=$timestamp"
    }

    private fun buildSearchUrl(keyword: String, page: Int): String {
        return "?ac=videolist&wd=${java.net.URLEncoder.encode(keyword, "UTF-8")}&pg=$page"
    }
}
