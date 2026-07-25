# LibreTV Android

LibreTV 的 Android TV 原生客户端。通过服务端代理播放视频，支持多服务器管理、播放历史、收藏。

## 与本项目的关系

```
                        ┌──────────────────────┐
                        │    LibreTV Server     │
                        │  (Node.js / Express)  │
                        │                       │
                        │  /proxy/ → 视频代理    │
                        │  /api/   → CMS V10 转发│
                        │  PASSWORD → 鉴权       │
                        └──────────┬───────────┘
                                   │ HTTP (部署在 Vercel/Netlify/EdgeOne/...)
                                   ▼
┌──────────────────────────────────────────────────────┐
│               LibreTV Android                         │
│                                                       │
│  OkHttp + Retrofit → 调用服务端 API                    │
│  ExoPlayer + ProxyDataSource → /proxy/ 播放视频       │
│  多服务器配置 / 播放历史 / 收藏 / Leanback UI           │
└──────────────────────────────────────────────────────┘
```

**关键约定：** 客户端**所有视频请求**走服务端 `/proxy/` 路由，不直连第三方视频源。搜索/详情同样走服务端转发。

---

## 技术栈

| 层 | 选型 | 版本/说明 |
|---|---|---|
| 语言 | **Kotlin** | 1.9+ |
| UI | **Leanback** | androidx.leanback (稳定成熟) |
| 播放器 | **ExoPlayer** | androidx.media3 |
| 网络 | **OkHttp + Retrofit + Moshi** | OkHttp 拦截器注入 auth 参数 |
| 图片 | **Coil** | 原生 Leanback CardPresenter 集成 |
| 架构 | **MVVM + Repository** | 标准分层 |
| DI | **Hilt** | 依赖注入 |
| 持久化 | **DataStore / SharedPreferences** | 多服务器配置 + 播放历史 |
| 最低 API | **21 (Android 5.0)** | 覆盖 99.9% 电视设备 |
| 编译 SDK | **34** | |

---

## 项目结构

```
LibreTV-Android/
├── app/
│   ├── src/main/
│   │   ├── java/com/libretv/android/
│   │   │   ├── App.kt                          # Hilt Application
│   │   │   ├── MainActivity.kt                 # Leanback 入口
│   │   │   │
│   │   │   ├── navigation/
│   │   │   │   └── Router.kt                   # 页面路由
│   │   │   │
│   │   │   ├── ui/
│   │   │   │   ├── browse/                     # 首页 (BrowseFragment)
│   │   │   │   ├── search/                     # 搜索页
│   │   │   │   ├── detail/                     # 视频详情 + 剧集列表
│   │   │   │   ├── player/                     # 播放 (ExoPlayer)
│   │   │   │   └── settings/                   # 服务器管理
│   │   │   │
│   │   │   ├── data/
│   │   │   │   ├── remote/
│   │   │   │   │   ├── LibreTVApi.kt           # Retrofit 接口
│   │   │   │   │   ├── ProxyInterceptor.kt     # OkHttp 拦截器 (auth)
│   │   │   │   │   └── dto/                    # CMS V10 响应模型
│   │   │   │   ├── repository/
│   │   │   │   │   └── VideoRepository.kt      # 数据仓库
│   │   │   │   └── local/
│   │   │   │       └── ServerConfigStore.kt    # 服务器配置持久化
│   │   │   │
│   │   │   ├── player/
│   │   │   │   ├── ProxyDataSource.kt          # DataSource.Factory 改写分片 URL
│   │   │   │   └── ProxyMediaSource.kt         # 带 auth 的 MediaSource
│   │   │   │
│   │   │   ├── model/
│   │   │   │   ├── ServerConfig.kt             # 服务器配置
│   │   │   │   ├── VideoItem.kt                # 视频条目
│   │   │   │   └── Episode.kt                 # 剧集
│   │   │   │
│   │   │   └── util/
│   │   │       ├── Sha256.kt                  # SHA-256 工具
│   │   │       └── Constants.kt               # 常量
│   │   │
│   │   ├── res/
│   │   │   ├── layout/                         # Leanback 布局
│   │   │   ├── drawable/                       # 图标/图片
│   │   │   ├── values/                         # 主题/字符串
│   │   │   └── xml/                            # 搜索配置等
│   │   │
│   │   └── AndroidManifest.xml
│   │
│   ├── build.gradle.kts                       # App 模块构建
│   └── proguard-rules.pro
│
├── docs/
│   ├── ARCHITECTURE.md                        # 详细架构
│   └── API.md                                 # 与服务端的 API 约定
│
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
│
├── build.gradle.kts                           # 根项目构建
├── settings.gradle.kts                        # 项目设置
├── gradle.properties                          # Gradle 配置
├── gradlew                                    # Gradle Wrapper (Linux)
├── gradlew.bat                                # Gradle Wrapper (Windows)
└── README.md                                  # 本文件
```

---

## 核心数据流

### 搜索（走服务端代理）

```
BrowseFragment → SearchFragment → SearchViewModel.search(server, keyword)
    ↓
VideoRepository.search(serverUrl, keyword)
    ↓
LibreTVApi.search(proxyUrl)
    ↓
LibreTV 服务端 /proxy/ 转发请求到 CMS V10 源
    ↓
第三方 CMS V10 API (ac=videolist&wd=keyword)
    ↓
SearchResponse → VideoItem[] → UI 展示
```

### 播放

```
DetailFragment → "播放" → PlaybackActivity
    ↓
ExoPlayer.setMediaItem(proxyUrl)
    ↓
ProxyDataSource.open()
    ↓  对每个请求 URL:
       1. 检测 URL 是否已含 /proxy/ 前缀
       2. 如已含 → 追加 auth 参数
       3. 如不含 → 用 /proxy/ 包装并附加 auth 参数
    ↓  HTTP
LibreTV 服务端 /proxy/encodedUrl?auth=xxx&t=xxx
    ↓
服务端: 鉴权 → 获取上游 → M3U8 改写 (如需要) → 返回
    ↓
ExoPlayer 播放
```

### 多服务器切换

```
SettingsFragment
  ├── 服务器列表 (RecyclerView)
  │   ├── 服务器 A (选中)    → 搜索/播放走 A
  │   ├── 服务器 B (未选中)  → 显示但不用
  │   └── [+ 添加服务器]     → 输入 URL + 密码
  │
  └── 当前选中服务器高亮
      ↓
切换 = 更新 ActiveServerConfig (DataStore)
  ↓
VideoRepository 和 ProxyDataSource 读取最新配置
```

---

## 与服务端的 API 约定

详见 `docs/API.md`。简要说明：

| 端点 | 方法 | 用途 | 参数 |
|---|---|---|---|
| `{server}/proxy/{encodedUrl}?auth={hash}&t={ts}` | GET | 视频代理 | 由 `ProxyDataSource` 自动构造 |
| `{server}/proxy/{encodedSearchUrl}?auth={hash}&t={ts}` | GET | 搜索/详情（复用代理通道） | CMS V10 URL 编码后作为 path |
| `{server}/api/env/index` | GET | 服务端健康检测 | - |

**所有数据请求**（搜索、详情、视频）**都经过服务端代理**，客户端不直接调用第三方 API。

---

## 构建与运行

```bash
# 克隆后打开 Android Studio
# Sync Gradle → 连接设备 → Run

# 首次启动需要配置服务器:
# 设置 → 添加服务器 → 输入你的 LibreTV 部署地址 + PASSWORD
```

---

## 许可

Apache 2.0 (与 LibreTV Server 一致)
