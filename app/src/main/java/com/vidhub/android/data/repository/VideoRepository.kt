package com.vidhub.android.data.repository

import com.vidhub.android.data.local.ServerConfigStore
import com.vidhub.android.data.local.WatchHistoryItem
import com.vidhub.android.data.local.WatchHistoryStore
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.vidhub.android.data.remote.VidHubApi
import com.vidhub.android.data.remote.dto.SourceInfo
import com.vidhub.android.data.remote.dto.SourcesResponse
import com.vidhub.android.data.remote.dto.toVideoItem
import com.vidhub.android.model.CustomSource
import com.vidhub.android.model.Episode
import com.vidhub.android.model.ServerConfig
import com.vidhub.android.model.VideoItem
import com.vidhub.android.util.Sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** Result wrapper for [VideoRepository.fetchSources] that carries error detail. */
sealed class FetchSourcesResult {
    data class Success(val sources: List<SourceInfo>) : FetchSourcesResult()
    data class Error(val message: String) : FetchSourcesResult()
}

@Singleton
class VideoRepository @Inject constructor(
    private val api: VidHubApi,
    private val okHttp: OkHttpClient,
    private val serverConfigStore: ServerConfigStore,
    private val watchHistoryStore: WatchHistoryStore
) {
    // In-memory cache of server source lists (keyed by server URL)
    private var sourcesCache: Map<String, List<SourceInfo>> = emptyMap()

    fun getServers(): Flow<List<ServerConfig>> = serverConfigStore.getServers()

    fun getActiveServer(): Flow<ServerConfig?> = serverConfigStore.getActiveServer()

    fun getActiveServerSync(): ServerConfig? = serverConfigStore.getActiveServerSync()

    suspend fun addServer(config: ServerConfig) = serverConfigStore.addServer(config)

    suspend fun updateServer(config: ServerConfig) = serverConfigStore.updateServer(config)

    suspend fun removeServer(id: String) = serverConfigStore.removeServer(id)

    suspend fun setActiveServer(id: String) = serverConfigStore.setActiveServer(id)

    suspend fun fetchSources(server: ServerConfig): FetchSourcesResult {
        return try {
            Log.d("VideoRepository", "Step 1: building URL...")
            val url = buildVidHubUrl(server, "/api/sources", emptyMap())
            Log.d("VideoRepository", "Step 2: calling API from: ${url.take(100)}...")
            val request = Request.Builder().url(url).build()
            val call = okHttp.newCall(request)
            val httpResponse = withContext(Dispatchers.IO) { call.execute() }
            Log.d("VideoRepository", "Step 3: HTTP ${httpResponse.code}")
            val json = httpResponse.body?.string() ?: ""
            Log.d("VideoRepository", "Step 4: Raw response: ${json.take(200)}")
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
            val adapter = moshi.adapter(SourcesResponse::class.java)
            val parsed = adapter.fromJson(json)
            if (parsed == null) {
                FetchSourcesResult.Error("服务器返回空响应")
            } else if (parsed.code == 200 && parsed.sources != null) {
                sourcesCache = sourcesCache + (server.url.trimEnd('/') to parsed.sources)
                FetchSourcesResult.Success(parsed.sources)
            } else {
                val msg = parsed.msg ?: "服务器返回状态码 ${parsed.code}"
                Log.w("VideoRepository", "/api/sources returned code=${parsed.code}: $msg")
                FetchSourcesResult.Error(msg)
            }
        } catch (e: IOException) {
            Log.e("VideoRepository", "fetchSources network error", e)
            FetchSourcesResult.Error("网络错误: ${e.localizedMessage ?: e.message ?: "未知错误"}")
        } catch (e: Exception) {
            Log.e("VideoRepository", "fetchSources unexpected error", e)
            val stack = e.stackTraceToString().substringBefore('\n')
            FetchSourcesResult.Error("${e.javaClass.simpleName}: ${e.message}\n$stack")
        }
    }

    /** Get cached sources for a server URL. */
    fun getCachedSources(serverUrl: String): List<SourceInfo> {
        return sourcesCache[serverUrl.trimEnd('/')] ?: emptyList()
    }

    /** Update a server's enabled sources and custom sources. */
    suspend fun updateServerSources(
        serverId: String,
        enabledSources: List<String>,
        customSources: List<CustomSource>
    ) {
        val server = serverConfigStore.getServerById(serverId) ?: return
        serverConfigStore.updateServer(
            server.copy(
                enabledSources = enabledSources,
                customSources = customSources
            )
        )
    }

    suspend fun search(server: ServerConfig, keyword: String, page: Int = 1): Result<List<VideoItem>> {
        return try {
            val cmsUrls = mutableListOf<String>()
            val enabledKeys = server.enabledSources
            if (enabledKeys.isNotEmpty()) {
                val sources = getCachedSources(server.url.trimEnd('/'))
                enabledKeys.forEach { key ->
                    sources.find { it.key == key }?.let { cmsUrls.add(it.api) }
                }
            }
            server.customSources.forEach { cmsUrls.add(it.url) }

            if (cmsUrls.isEmpty()) return@search Result.success(emptyList())

            val results = cmsUrls.mapNotNull { cmsUrl ->
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
        val queryParts = mutableListOf<String>()
        queryParts.addAll(params.entries.map {
            "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
        })
        queryParts.add("auth=$hash")
        queryParts.add("t=$timestamp")
        return "$base$path?${queryParts.joinToString("&")}"
    }
}
