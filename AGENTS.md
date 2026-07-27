# VidHub Android — Agent Instructions

## 项目简介

VidHub 的 Android TV 原生客户端。通过 VidHub 服务端 API 搜索视频和获取播放地址，支持多服务器管理（多 VidHub 实例）、播放历史、收藏。

## 依赖项目

依赖 [VidHub Server](https://github.com/c2726139513/VidHub)（`/root/VidHub`）作为后端服务。

VidHub API 端点：
- `GET /api/search?wd=&apiUrl=&auth=&t=` — 搜索
- `GET /api/detail?id=&apiUrl=&auth=&t=` — 视频详情+剧集列表
- `GET /api/env/password` — 获取密码哈希

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
| 播放器 | ExoPlayer (androidx.media3) — 直连播放 M3U8 |
| 网络 | OkHttp + Retrofit + Moshi |
| 图片 | Coil |
| DI | Hilt |
| 持久化 | DataStore / SharedPreferences |

## 关键架构规则

1. **直连播放** — 视频 URL 直接从 VidHub `/api/detail` 获取，ExoPlayer 直连播放，不走代理。

2. **搜索/详情走 VidHub API** — 搜索和详情请求通过 Retrofit 直连 VidHub 服务端，不直连第三方 CMS。

3. **多服务器独立鉴权** — 每个服务器独立存储 URL+密码，切换服务器时搜索和播放自动切换目标。每个服务器是一个独立的 VidHub 部署实例。

4. **密码加密存储** — 使用 EncryptedSharedPreferences 存储用户密码。

5. **Auth 参数** — `auth=sha256(password)&t=timestamp` 作为每个 API 请求的查询参数，在 `VideoRepository.buildVidHubUrl()` 中构造。

## 核心文件

| 文件 | 职责 |
|---|---|
| `MainActivity.kt` | Leanback 入口，加载 BrowseFragment |
| `ui/browse/BrowseFragment.kt` | 首页：服务器行 + 继续观看 + 收藏 |
| `ui/search/SearchFragment.kt` | 搜索 |
| `ui/detail/DetailFragment.kt` | 视频详情 + 剧集列表 |
| `ui/player/PlaybackActivity.kt` | ExoPlayer 全屏播放（直连） |
| `ui/settings/SettingsFragment.kt` | 服务器管理 |
| `data/remote/VidHubApi.kt` | Retrofit 接口（/api/search, /api/detail, /api/env/password） |
| `data/remote/dto/DetailResponse.kt` | VidHub 详情 API 响应 DTO |
| `data/remote/dto/SearchResponse.kt` | CMS V10 搜索响应 DTO |
| `data/repository/VideoRepository.kt` | 统一数据出口，构建 VidHub URL + auth |
| `data/local/ServerConfigStore.kt` | 服务器配置持久化 |
| `model/ServerConfig.kt` | 服务器配置模型（URL + 密码 + CMS 源列表） |

## 数据流

```
搜索: SearchFragment → SearchVM → VideoRepository → VidHubApi.search(url) → /api/search → VidHub Server → CMS V10
详情: DetailFragment → DetailVM → VideoRepository → VidHubApi.detail(url) → /api/detail → VidHub Server → episodes + videoInfo
播放: PlaybackActivity → ExoPlayer → HlsMediaSource → 直连 M3U8 URL（无代理）
鉴权: VideoRepository.buildVidHubUrl() → sha256(password) + timestamp → auth 查询参数
多服务器: SettingsFragment → ServerConfigStore (切换) → VideoRepository 读取活跃配置
```

## API 响应格式

### 搜索 (`/api/search`)
```json
{ "code": 200, "list": [{ "vod_id": "...", "vod_name": "...", "vod_pic": "..." }], "pagecount": 1 }
```

### 详情 (`/api/detail`)
```json
{
  "code": 200,
  "episodes": ["https://...m3u8", "https://...m3u8"],
  "videoInfo": { "title": "...", "cover": "...", "desc": "...", "type": "...", "year": "...", "area": "...", "director": "...", "actor": "...", "remarks": "...", "source_name": "...", "source_code": "..." }
}
```

## 重要注意事项

1. **auth 参数在 URL 中构造，不在拦截器或 DataSource 中** — `VideoRepository.buildVidHubUrl()` 统一构建带 auth 的 API URL。

2. **M3U8 播放直连** — 不再需要 ProxyDataSource 或 ProxyMediaSource。ExoPlayer 直接播放 VidHub 返回的 URL。

3. **剧集从详情 API 获取** — 搜索返回的结果不含剧集列表。用户点击某视频后，DetailViewModel 调用 `/api/detail` 获取剧集 URL。

4. **多服务器 = 多 VidHub 实例** — 每个服务器配置一个 VidHub 部署地址+密码。不同实例可以对接不同的 CMS 源。

5. **Leanback 导航层次** — BrowseFragment 的 Adapter 层级：`BrowseAdapter ← RowsFragment ← ArrayObjectAdapter ← ListRow ← Header + ArrayObjectAdapter ← CardPresenter`
