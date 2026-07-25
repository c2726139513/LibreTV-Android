# LibreTV Android — Agent Instructions

## 项目简介

LibreTV 的 Android TV 原生客户端。通过服务端代理播放视频，支持多服务器管理、播放历史、收藏。

## 依赖项目

依赖 [LibreTV Server](https://github.com/LibreSpark/LibreTV) 作为后端代理服务，客户端不做视频源直连播放。

## 构建环境

- Android Studio Hedgehog 2023.1+ / Ladybug 2024.2+
- JDK 17
- Gradle 8.5+
- Kotlin 1.9+
- Android SDK 34 (compileSdk), 21 (minSdk)

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin |
| UI | Leanback (稳定 API 21 兼容) |
| 播放器 | ExoPlayer (androidx.media3) |
| 网络 | OkHttp + Retrofit + Moshi |
| 图片 | Coil |
| DI | Hilt |
| 持久化 | DataStore / SharedPreferences |

## 关键架构规则

1. **视频必须走 `/proxy/` 代理** — ExoPlayer 的所有视频请求通过 ProxyDataSource 改写，经 LibreTV 服务端代理转发。禁止直连第三方视频 URL。

2. **搜索详情走服务端代理** — 搜索和详情请求**走 LibreTV 服务端的 `/proxy/` 通道**，与服务端转发的 CMS 源通信，不直连第三方 API。隐藏上游源地址，统一鉴权。

3. **多服务器独立鉴权** — 每个服务器独立存储 URL+密码，切换服务器时搜索和播放自动切换目标。

4. **密码加密存储** — 使用 EncryptedSharedPreferences 存储用户密码。

## 核心文件

| 文件 | 职责 |
|---|---|
| `MainActivity.kt` | Leanback 入口，加载 BrowseFragment |
| `ui/browse/BrowseFragment.kt` | 首页：服务器行 + 继续观看 + 收藏 |
| `ui/search/SearchFragment.kt` | 搜索 (Leanback SearchFragment) |
| `ui/detail/DetailFragment.kt` | 视频详情 + 剧集列表 |
| `ui/player/PlaybackActivity.kt` | ExoPlayer 全屏播放 |
| `ui/settings/SettingsFragment.kt` | 服务器管理 |
| `data/remote/LibreTVApi.kt` | Retrofit 接口 |
| `data/remote/ProxyInterceptor.kt` | OkHttp 拦截器注入 auth 参数 |
| `data/repository/VideoRepository.kt` | 统一数据出口 |
| `data/local/ServerConfigStore.kt` | 服务器配置持久化 |
| `player/ProxyDataSource.kt` | ExoPlayer DataSource 改写 URL |
| `model/ServerConfig.kt` | 服务器配置模型 |

## 数据流

```
搜索: SearchFragment → SearchVM → VideoRepository → LibreTVApi → /proxy/ → Server → CMS V10 → SearchResponse → UI
播放: DetailFragment → PlaybackActivity → ExoPlayer → ProxyDataSource → /proxy/ → Server → 上游源
鉴权: ProxyInterceptor / ProxyDataSource → sha256(password) + timestamp → auth 参数
多服务器: SettingsFragment → ServerConfigStore (切换) → VideoRepository / ProxyDataSource 读取活跃配置
```

## 重要注意事项

1. **ProxyInterceptor 和 ProxyDataSource 不要双重注入 auth** — 如果一个在 OkHttp 层注入，另一个在 DataSource 层也注入，会产生重复参数。选择在 ProxyDataSource 层处理 auth 注入，ProxyInterceptor 只做 CMS 请求的 User-Agent 设置。

2. **M3U8 改写由服务端完成** — 客户端不处理 M3U8 解析，所有 M3U8 请求发送到 `/proxy/` 后由服务端改写内部分片 URL。

3. **CMS V10 URL 解析** — `vod_play_url` 格式为 `名$url#名$url`，使用 `split("$")` 和 `split("#")` 解析。

4. **Leanback 导航层次** — BrowseFragment 的 Adapter 层级：`BrowseAdapter ← RowsFragment ← ArrayObjectAdapter ← ListRow ← Header + ArrayObjectAdapter ← CardPresenter`
