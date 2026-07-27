# VidHub Android

VidHub 的 Android TV 原生客户端。通过 VidHub 服务端 API 搜索视频和获取播放地址，支持多服务器管理（多 VidHub 实例）、播放历史、收藏。

## 与本项目的关系

```
                        ┌──────────────────────┐
                        │     VidHub Server     │
                        │   (Next.js / React)   │
                        │                       │
                        │  /api/search → 搜索    │
                        │  /api/detail → 详情    │
                        │  /api/env/password     │
                        │  PASSWORD → 鉴权       │
                        └──────────┬───────────┘
                                   │ HTTP (部署在 Vercel/Netlify/EdgeOne/...)
                                   ▼
┌──────────────────────────────────────────────────────┐
│               VidHub Android                          │
│                                                       │
│  OkHttp + Retrofit → 调用服务端 API                    │
│  ExoPlayer → 直连播放 M3U8（无代理）                   │
│  多服务器配置 / 播放历史 / 收藏 / Leanback UI           │
└──────────────────────────────────────────────────────┘
```

**关键约定：** 搜索和详情走 VidHub 服务端 API，视频直连播放（VidHub 不代理视频流）。

---

## 技术栈

| 层 | 选型 | 版本/说明 |
|---|---|---|
| 语言 | **Kotlin** | 1.9+ |
| UI | **Leanback** | androidx.leanback (稳定成熟) |
| 播放器 | **ExoPlayer** | androidx.media3 |
| 网络 | **OkHttp + Retrofit + Moshi** | Retrofit 直连 VidHub API |
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
│   │   ├── java/com/vidhub/android/
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
│   │   │   │   │   ├── VidHubApi.kt            # Retrofit 接口
│   │   │   │   │   └── dto/                    # API 响应模型
│   │   │   │   ├── repository/
│   │   │   │   │   └── VideoRepository.kt      # 数据仓库，构建 auth URL
│   │   │   │   └── local/
│   │   │   │       └── ServerConfigStore.kt    # 服务器配置持久化
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

### 搜索（走 VidHub API）

```
BrowseFragment → SearchFragment → SearchViewModel.search(server, keyword)
    ↓
VideoRepository.search(serverUrl, keyword)
    ↓  调用 VidHubApi.search(url) 其中 url = {server}/api/search?wd=keyword&apiUrl=cmsUrl&auth=sha256(pwd)&t=ts
VidHub 服务端 /api/search 转发请求到 CMS V10 源
    ↓
第三方 CMS V10 API (ac=videolist&wd=keyword)
    ↓
SearchResponse → VideoItem[] → UI 展示
```

### 详情

```
DetailFragment → DetailViewModel.loadDetail(vodId)
    ↓
VideoRepository.detail(server, vodId)
    ↓  调用 VidHubApi.detail(url) 其中 url = {server}/api/detail?id=vodId&apiUrl=cmsUrl&auth=sha256(pwd)&t=ts
VidHub 服务端 /api/detail 转发请求到 CMS V10 源
    ↓
返回 { episodes: string[], videoInfo: { title, cover, desc, ... } }
    ↓
DetailFragment 展示视频信息 + 剧集列表
```

### 播放

```
DetailFragment → 点击剧集 → PlaybackActivity
    ↓
ExoPlayer.setMediaItem(directM3u8Url)
    ↓
HlsMediaSource → 直连 M3U8 URL（无代理）
    ↓
ExoPlayer 播放
```

### 多服务器切换

```
SettingsFragment
  ├── 服务器列表 (RecyclerView)
  │   ├── VidHub 实例 A (选中)  → 搜索/播放走 A
  │   ├── VidHub 实例 B (未选中) → 显示但不用
  │   └── [+ 添加服务器]        → 输入 URL + 密码
  │
  └── 当前选中服务器高亮
      ↓
切换 = 更新 ActiveServerConfig (DataStore)
  ↓
VideoRepository 读取活跃配置，切换 API 目标地址
```

---

## 与服务端的 API 约定

详见 `docs/API.md`。简要说明：

| 端点 | 方法 | 用途 | 参数 |
|---|---|---|---|
| `{server}/api/search?wd=&apiUrl=&auth=&t=` | GET | 搜索 | wd(关键词), apiUrl(CMS源), auth, t |
| `{server}/api/detail?id=&apiUrl=&auth=&t=` | GET | 视频详情+剧集 | id(视频ID), apiUrl(CMS源), auth, t |
| `{server}/api/env/password` | GET | 获取密码哈希 | - |

**数据搜索和详情请求**走 VidHub API 转发，**视频直连播放**（VidHub 不代理视频流）。

---

## 构建与运行

```bash
# 克隆后打开 Android Studio
# Sync Gradle → 连接设备 → Run

# 首次启动需要配置服务器:
# 设置 → 添加服务器 → 输入你的 VidHub 部署地址 + PASSWORD
```

---

## 许可

Apache 2.0 (与 VidHub Server 一致)
