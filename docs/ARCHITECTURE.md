# LibreTV Android — 架构文档

## 1. 整体分层

```
┌──────────────────────────────────────────────────────────┐
│                     UI 层 (Leanback)                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐  │
│  │ Browse   │ │ Search   │ │ Detail   │ │ Settings   │  │
│  │ Fragment │ │ Fragment │ │ Fragment │ │ Fragment   │  │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └──────┬─────┘  │
│       │            │            │               │        │
│  ┌────▼────────────▼────────────▼───────────────▼─────┐  │
│  │               ViewModel 层 (Hilt)                   │  │
│  │  BrowseVM  SearchVM  DetailVM  PlayerVM  SettingsVM │  │
│  └───────────────────────┬────────────────────────────┘  │
│                          │                                │
├──────────────────────────┼──────────────────────────────┤
│  Data 层                 │                               │
│                          │                                │
│  ┌───────────────────────▼────────────────────────────┐  │
│  │              VideoRepository                       │  │
│  │  - search(server, query, page)                     │  │
│  │  - detail(server, id)                              │  │
│  │  - getServers() / setActiveServer()                │  │
│  │  - saveWatchHistory() / getWatchHistory()          │  │
│  └────┬──────────────────────┬────────────────────────┘  │
│       │                      │                            │
│  ┌────▼────┐          ┌──────▼──────┐                    │
│  │Remote   │          │ Local       │                    │
│  │LibreTV  │          │ServerConfig │                    │
│  │Api      │          │Store        │                    │
│  │(Retrofit│          │(DataStore)  │                    │
│  │+Moshi)  │          │HistoryStore │                    │
│  └────┬────┘          │(Room/SQLite)│                    │
│       │               └─────────────┘                    │
│       │                                                   │
│  ┌────▼──────────────────────────────────────────────┐   │
│  │         OkHttp Client (+ ProxyInterceptor)         │   │
│  │  - 为 /proxy/ 请求注入 auth + timestamp            │   │
│  │  - 为 CMS API 请求设置 User-Agent/Referer          │   │
│  └───────────────────────────────────────────────────┘   │
│                                                           │
│  ┌───────────────────────────────────────────────────┐   │
│  │         ExoPlayer + ProxyDataSource                │   │
│  │  - DataSource.Factory 拦截每个分片请求              │   │
│  │  - 自动拼 /proxy/ 前缀 + auth 参数                 │   │
│  │  - 支持 HLS (M3U8) + AES-128 解密                 │   │
│  └───────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────┘
```

## 2. 模块职责

### 2.1 UI 层

#### BrowseFragment (首页)
- `BrowseFragment` 继承自 `androidx.leanback.app.BrowseFragment`
- 左侧**行列表 (RowsFragment)** = 服务器列表 + "继续观看" + "收藏"
- 右侧内容区展示：当前选中服务器的推荐内容 / 最近观看
- 遥控器 D-pad 导航：左右切换行，上下切换卡片

```
BrowseFragment 布局:
┌──────────────────────────────────────────┐
│  [Logo]                    设置 ⚙️       │ │ ← HeaderView
├──────────────────────────────────────────┤
│ ┌ 我的服务器                              │
│ │  │ 海报1  │ 海报2  │ 海报3  │ ...       │ │ ← RowsFragment
│ │ ─────────────────────────────────────   │
│ │ 继续观看                               │
│ │  │ 海报1  │ 海报2  │ ...                  │
│ │ ─────────────────────────────────────   │
│ └ 收藏                                   │
│    │ 海报1  │ 海报2  │ ...                  │
└──────────────────────────────────────────┘
```

#### SearchFragment (搜索)
- 继承 Leanback 的 `SearchFragment`
- 用户输入 → debounce 500ms → 发起搜索请求
- 结果展示为 `ArrayObjectAdapter` + `CardPresenter`
- 选中结果 → 导航到 DetailFragment

#### DetailFragment (详情)
- 继承 `DetailsSupportFragment`
- logo 行 → 视频标题 + 描述
- 操作行 → 播放 / 添加到收藏
- 剧集行 → 横向网格，选中后播放对应集
- 数据加载 → 调用 CMS V10 detail API

#### PlaybackActivity (播放)
- 全屏 `ExoPlayer` + `PlayerView`
- 播放器控制：暂停/继续、快进/快退、选集、切换服务器
- 退出时保存播放进度到本地

#### SettingsFragment (设置)
- 服务器列表管理：添加/编辑/删除/切换
- 每个服务器配置：URL + 密码
- 查看存储空间使用情况

### 2.2 Data 层

#### LibreTVApi (Retrofit)

搜索/详情请求复用 `/proxy/` 通道。客户端将 CMS V10 URL 编码后作为 `/proxy/` 的 path 发送，服务端解码后转发到目标 CMS V10 API。

```kotlin
interface LibreTVApi {
    // 搜索 — 通过服务端 /proxy/ 转发
    @GET
    suspend fun search(
        @Url url: String,  // {server}/proxy/{encodedCmsSearchUrl}?auth=xxx&t=xxx
    ): SearchResponse

    // 详情 — 通过服务端 /proxy/ 转发
    @GET
    suspend fun detail(
        @Url url: String,  // {server}/proxy/{encodedCmsDetailUrl}?auth=xxx&t=xxx
    ): DetailResponse

    // 代理视频 — 通过 /proxy/ 拉流
    // 注意: 此端点由 ProxyDataSource 触发，不由 Repository 直接调用
    @Streaming
    @GET
    suspend fun proxyVideo(
        @Url encodedUrl: String,
    ): ResponseBody
}
```

#### ProxyInterceptor (OkHttp Interceptor)
拦截所有发往 `/proxy/` 的 HTTP 请求，自动注入 `auth` 和 `t` 参数：

```kotlin
class ProxyInterceptor(
    private val serverConfigProvider: () -> ServerConfig?
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url

        // 只处理 /proxy/ 路径的请求
        if (!url.encodedPath.contains("/proxy/")) {
            return chain.proceed(original)
        }

        val config = serverConfigProvider()
            ?: return chain.proceed(original) // 无配置则放行

        val timestamp = System.currentTimeMillis()
        val authHash = sha256(config.password) // 注意: 存储的是原始密码用于计算

        val newUrl = url.newBuilder()
            .addQueryParameter("auth", authHash)
            .addQueryParameter("t", timestamp.toString())
            .build()

        val newRequest = original.newBuilder().url(newUrl).build()
        return chain.proceed(newRequest)
    }
}
```

**密码存储策略：** 用户输入密码后，本地存的是**原始密码**（用于每次请求重新计算 SHA-256）。因为服务端要求的是 `sha256(password)` 而不是直接用 hash 做 token。存储使用 EncryptedSharedPreferences。

#### ServerConfigStore (本地持久化)
```kotlin
data class ServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,           // 用户自定义名称
    val url: String,            // 例: https://libretv.example.com
    val password: String,       // 原始密码 (加密存储)
    val isActive: Boolean = false,
)

class ServerConfigStore(private val context: Context) {
    // 加密存储所有服务器配置
    fun getServers(): Flow<List<ServerConfig>>
    suspend fun addServer(config: ServerConfig)
    suspend fun removeServer(id: String)
    suspend fun setActiveServer(id: String)
    fun getActiveServer(): Flow<ServerConfig?>
}
```

#### WatchHistoryStore (播放历史)
```kotlin
data class WatchHistoryItem(
    val videoId: String,
    val title: String,
    val coverUrl: String,
    val serverId: String,
    val episodeIndex: Int,
    val position: Long,         // 毫秒
    val duration: Long,
    val lastWatched: Long,      // 时间戳
    val sourceName: String,
)

class WatchHistoryStore(private val context: Context) {
    // 使用 Room 或 DataStore
    fun getRecentHistory(limit: Int = 20): Flow<List<WatchHistoryItem>>
    suspend fun saveProgress(item: WatchHistoryItem)
    suspend fun deleteItem(videoId: String)
    suspend fun clearAll()
}
```

### 2.3 Player 层

#### ProxyDataSource
继承 ExoPlayer 的 `DataSource.Factory`，核心逻辑：

```kotlin
class ProxyDataSourceFactory(
    private val baseDataSourceFactory: DataSource.Factory,
    private val serverConfigProvider: () -> ServerConfig?,
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        return ProxyDataSource(
            baseDataSourceFactory.createDataSource(),
            serverConfigProvider
        )
    }
}

class ProxyDataSource(
    private val base: DataSource,
    private val serverConfigProvider: () -> ServerConfig?,
) : DataSource {

    override fun open(dataSpec: DataSpec): Long {
        val config = serverConfigProvider() ?: return base.open(dataSpec)

        // dataSpec.uri = 原始 URL
        // 例: https://cdn.example.com/segment.ts
        // 需要构造: https://server/proxy/https%3A%2F%2Fcdn.example.com%2Fsegment.ts?auth=xxx&t=xxx

        val proxyUrl = buildProxyUrl(config, dataSpec.uri)
        val newSpec = dataSpec.withUri(proxyUrl)
        return base.open(newSpec)
    }

    private fun buildProxyUrl(config: ServerConfig, originalUri: Uri): Uri {
        val timestamp = System.currentTimeMillis()
        val hash = sha256(config.password)
        val encoded = Uri.encode(originalUri.toString())
        val baseUrl = config.url.trimEnd('/')
        return Uri.parse("$baseUrl/proxy/$encoded?auth=$hash&t=$timestamp")
    }

    // 委托其他方法到 base DataSource
    override fun read(buffer: ByteArray, offset: Int, length: Int) = base.read(buffer, offset, length)
    override fun close() = base.close()
    // ...
}
```

## 3. 状态管理

```
AppState (单例, Hilt @Singleton)
├── activeServer: Flow<ServerConfig?>    ← 从 DataStore 读取
├── servers: Flow<List<ServerConfig>>    ← 所有服务器
├── watchHistory: Flow<List<WatchHistory>> ← 播放历史
└── favorites: Flow<Set<String>>         ← 收藏 ID 集合

各 ViewModel 观察所需 Flow，响应式更新 UI。
```

## 4. 依赖注入 (Hilt)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideOkHttpClient(interceptor: ProxyInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://placeholder/") // 实际 baseUrl 由构造时动态替换
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }

    @Provides @Singleton
    fun provideServerConfigStore(@ApplicationContext ctx: Context): ServerConfigStore {
        return ServerConfigStore(ctx)
    }

    @Provides @Singleton
    fun provideProxyDataSourceFactory(
        serverConfigProvider: ServerConfigProvider
    ): DataSource.Factory {
        val base = DefaultDataSource.Factory(context)
        return ProxyDataSourceFactory(base, serverConfigProvider)
    }
}
```

## 5. 关键设计决策

| 决策 | 选择 | 原因 |
|---|---|---|
| 搜索走服务端代理 | 搜索/详情请求通过 `/proxy/` 通道转发 | 隐藏上游源地址、统一鉴权、避免客户端被屏蔽 |
| 视频走服务端代理 | `ProxyDataSource` 强制走 `/proxy/` | 防盗链、Referer、IP 封禁问题一次解决 |
| 多服务器独立鉴权 | 每个服务器独立存储 URL+密码 | 用户可能有多个部署实例 |
| 密码加密存储 | `EncryptedSharedPreferences` | 安全存储原始密码 |
| 播放进度本地存储 | Room / DataStore | 离线可用，不依赖服务端 |
| Leanback | 非 Compose for TV | 稳定，API 21 兼容，D-pad 导航开箱即用 |

## 6. 性能考虑

- 搜索结果分页: 每页 20 条，懒加载
- 图片缓存: Coil 磁盘缓存 256MB
- 播放缓冲: ExoPlayer `LoadControl` 配置 (默认即可)
- 网络超时: 连接 15s，读取 30s
- 搜索防抖: 500ms debounce
