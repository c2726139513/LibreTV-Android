package com.vidhub.android.data.repository

import com.vidhub.android.data.local.FavoritesStore
import com.vidhub.android.data.local.ServerConfigStore
import com.vidhub.android.data.local.SourcesCacheStore
import com.vidhub.android.data.local.WatchHistoryStore
import com.vidhub.android.data.remote.VidHubApi
import com.vidhub.android.data.remote.dto.VideoInfoDto
import com.vidhub.android.data.remote.dto.toVideoItem
import com.vidhub.android.model.ApiSource
import com.vidhub.android.model.Episode
import com.vidhub.android.model.FavoriteItem
import com.vidhub.android.model.ServerConfig
import com.vidhub.android.model.VideoItem
import com.vidhub.android.model.WatchHistoryItem
import com.vidhub.android.util.Constants
import com.vidhub.android.util.Sha256
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/** API 层错误（code 来自 VidHub 响应体或 HTTP 状态码） */
class ApiException(val code: Int, message: String?) : Exception(message ?: "请求失败（$code）")

/**
 * 统一数据出口。
 *
 * 关键约定（见 AGENTS.md）：
 * 1. auth 参数在 [buildVidHubUrl] 中统一构造（auth=sha256(password)&t=timestamp），
 *    不使用拦截器，也不修改视频流地址。
 * 2. 搜索/详情走 VidHub 服务端 API（/api/search、/api/detail），不直连第三方 CMS。
 * 3. 视频播放地址由详情接口原样返回，ExoPlayer 直连播放，无代理。
 */
@Singleton
class VideoRepository @Inject constructor(
    private val api: VidHubApi,
    private val serverConfigStore: ServerConfigStore,
    private val sourcesCacheStore: SourcesCacheStore,
    private val watchHistoryStore: WatchHistoryStore,
    private val favoritesStore: FavoritesStore,
) {

    // ==================== URL 构造 ====================

    /**
     * 构造带鉴权参数的 VidHub API URL。
     * 例：{server}/api/search?wd=xx&apiUrl=xx&auth=sha256(pwd)&t=1712345678901
     */
    fun buildVidHubUrl(
        server: ServerConfig,
        path: String,
        params: Map<String, String?> = emptyMap(),
    ): String {
        val builder = (server.baseUrl + "/" + path.trimStart('/')).toHttpUrl().newBuilder()
        params.forEach { (key, value) -> value?.let { builder.addQueryParameter(key, it) } }
        builder.addQueryParameter("auth", Sha256.hex(server.password))
        builder.addQueryParameter("t", System.currentTimeMillis().toString())
        return builder.build().toString()
    }

    // ==================== 服务器 ====================

    val servers: Flow<List<ServerConfig>> = serverConfigStore.servers

    /** 当前选中服务器（未配置时为 null） */
    val activeServer: Flow<ServerConfig?> =
        combine(serverConfigStore.servers, serverConfigStore.activeServerId) { list, activeId ->
            list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
        }

    fun getActiveServerSnapshot(): ServerConfig? = serverConfigStore.getActiveServerSnapshot()

    // ==================== 数据源 ====================

    /** 从服务端拉取最新内置源列表并写入缓存 */
    suspend fun refreshSources(server: ServerConfig): List<ApiSource> {
        val response = api.getSources(buildVidHubUrl(server, "api/sources"))
        if (response.code != 200) throw ApiException(response.code, response.msg)
        val sources = response.sources.orEmpty().map { ApiSource(key = it.key, name = it.name, api = it.api) }
        sourcesCacheStore.put(server.id, sources)
        return sources
    }

    /**
     * 获取某服务器可用的全部数据源：内置源（缓存优先，无缓存时在线拉取）+ 用户自定义源。
     */
    suspend fun getSources(server: ServerConfig): List<ApiSource> {
        val cached = sourcesCacheStore.get(server.id)
        val builtin = cached.ifEmpty {
            runCatching { refreshSources(server) }.getOrDefault(emptyList())
        }
        return builtin + serverConfigStore.getCustomSources(server.id)
    }

    // ==================== 搜索 ====================

    sealed interface SearchEvent {
        /** 某个源成功返回一批结果 */
        data class SourceResult(
            val source: ApiSource,
            val items: List<VideoItem>,
            val pageCount: Int,
        ) : SearchEvent

        /** 某个源请求失败（网络错误或上游错误），不影响其他源 */
        data class SourceFailed(val source: ApiSource, val reason: String) : SearchEvent

        /** 鉴权失败（HTTP 401）：密码错误，本次搜索应中止 */
        data object AuthFailed : SearchEvent
    }

    /**
     * 聚合搜索：并发请求服务器的所有数据源，结果随源返回逐步发射。
     * 单个源失败不影响整体；遇到 401 时发射 [SearchEvent.AuthFailed] 并结束。
     */
    fun search(server: ServerConfig, keyword: String): Flow<SearchEvent> = channelFlow {
        val sources = getSources(server).take(Constants.SEARCH_MAX_SOURCES)
        if (sources.isEmpty()) return@channelFlow

        val queue = Channel<ApiSource>(capacity = Channel.UNLIMITED)
        sources.forEach { queue.trySend(it) }
        queue.close()

        // 任一源返回 401 时置位，其余 worker 停止取新任务
        val authFailed = java.util.concurrent.atomic.AtomicBoolean(false)

        val workerCount = minOf(Constants.SEARCH_MAX_CONCURRENT_SOURCES, sources.size)
        coroutineScope {
            repeat(workerCount) {
                launch {
                    for (source in queue) {
                        if (authFailed.get()) break
                        val event = runCatching {
                            val url = buildVidHubUrl(
                                server, "api/search",
                                mapOf("wd" to keyword, "apiUrl" to source.api, "pg" to "1"),
                            )
                            val response = api.search(url)
                            if (response.code == 200) {
                                val items = response.list.orEmpty()
                                    .mapNotNull { it.toVideoItem(source.name, source.api, server.id) }
                                SearchEvent.SourceResult(source, items, response.pagecount ?: 1)
                            } else {
                                SearchEvent.SourceFailed(source, response.msg ?: "错误码 ${response.code}")
                            }
                        }.getOrElse { error ->
                            if (error is HttpException && error.code() == 401) {
                                SearchEvent.AuthFailed
                            } else {
                                SearchEvent.SourceFailed(source, error.message ?: "网络错误")
                            }
                        }
                        send(event)
                        if (event is SearchEvent.AuthFailed) {
                            authFailed.set(true)
                            break
                        }
                    }
                }
            }
        }
    }

    // ==================== 详情 ====================

    data class Detail(
        val info: VideoInfoDto?,
        val episodes: List<Episode>,
        val detailUrl: String?,
    )

    /**
     * 获取视频详情与剧集列表。
     * 若条目来自带"网页详情地址"的自定义源，自动改用服务端网页抓取模式（customDetail 参数）。
     */
    suspend fun detail(server: ServerConfig, item: VideoItem): Detail {
        val customDetail = serverConfigStore.getCustomSources(server.id)
            .firstOrNull { it.api == item.sourceApi }
            ?.detailUrl
            ?.takeIf { it.isNotBlank() }
        val response = try {
            api.detail(
                buildVidHubUrl(
                    server, "api/detail",
                    mapOf("id" to item.vodId, "apiUrl" to item.sourceApi, "customDetail" to customDetail),
                )
            )
        } catch (e: HttpException) {
            throw ApiException(e.code(), if (e.code() == 401) "鉴权失败，请检查服务器密码" else e.message())
        }
        if (response.code != 200) throw ApiException(response.code, response.msg)
        return Detail(
            info = response.videoInfo,
            episodes = Episode.fromUrls(response.episodes.orEmpty()),
            detailUrl = response.detailUrl,
        )
    }

    // ==================== 服务器密码校验 ====================

    enum class VerifyResult {
        /** 密码正确，可以使用 */
        OK,

        /** 服务器可达，但密码不匹配 */
        WRONG_PASSWORD,

        /** 服务器未配置 PASSWORD 环境变量（该实例无法对外提供服务） */
        NO_PASSWORD_ON_SERVER,

        /** 网络不可达或响应异常 */
        NETWORK_ERROR,
    }

    /** 通过 /api/env/password 返回的哈希校验用户输入的密码 */
    suspend fun verifyServer(serverUrl: String, password: String): VerifyResult {
        val base = serverUrl.trim().trimEnd('/')
            .let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
        return try {
            val response = api.getPasswordHash("$base/api/env/password")
            when {
                response.hash == null -> VerifyResult.NO_PASSWORD_ON_SERVER
                response.hash.equals(Sha256.hex(password), ignoreCase = true) -> VerifyResult.OK
                else -> VerifyResult.WRONG_PASSWORD
            }
        } catch (e: Exception) {
            VerifyResult.NETWORK_ERROR
        }
    }

    // ==================== 播放历史 ====================

    val history: Flow<List<WatchHistoryItem>> = watchHistoryStore.history

    suspend fun getHistory(key: String): WatchHistoryItem? = watchHistoryStore.get(key)

    suspend fun saveProgress(
        item: VideoItem,
        episodeIndex: Int,
        episodeCount: Int,
        positionMs: Long,
        durationMs: Long,
    ) {
        // 接近结尾视为看完，下次从头播
        val nearEnd = durationMs > 0 && durationMs - positionMs < Constants.HISTORY_NEAR_END_MS
        watchHistoryStore.saveProgress(
            WatchHistoryItem(
                key = item.stableKey,
                vodId = item.vodId,
                sourceApi = item.sourceApi,
                serverId = item.serverId,
                title = item.title,
                coverUrl = item.coverUrl,
                episodeIndex = episodeIndex,
                episodeCount = episodeCount,
                positionMs = if (nearEnd) 0L else positionMs,
                durationMs = durationMs,
            )
        )
    }

    suspend fun removeHistory(key: String) = watchHistoryStore.remove(key)

    suspend fun clearHistory() = watchHistoryStore.clear()

    // ==================== 收藏 ====================

    val favorites: Flow<List<FavoriteItem>> = favoritesStore.favorites

    suspend fun isFavorite(key: String): Boolean = favoritesStore.isFavorite(key)

    /** 切换收藏，返回切换后是否已收藏 */
    suspend fun toggleFavorite(item: VideoItem): Boolean = favoritesStore.toggle(
        FavoriteItem(
            key = item.stableKey,
            vodId = item.vodId,
            sourceApi = item.sourceApi,
            serverId = item.serverId,
            title = item.title,
            coverUrl = item.coverUrl,
            remarks = item.remarks,
        )
    )

    suspend fun removeFavorite(key: String) = favoritesStore.remove(key)
}
