# VidHub Android

VidHub 的 Android TV 原生客户端。通过 VidHub 服务端 API 搜索视频和获取播放地址，支持多服务器管理（多 VidHub 实例）、播放历史、收藏。

## 架构关系

```
                        ┌──────────────────────┐
                        │     VidHub Server     │
                        │   (Next.js / React)   │
                        │                       │
                        │  /api/search → 搜索    │
                        │  /api/detail → 详情    │
                        │  /api/sources → 数据源 │
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
| 语言 | **Kotlin** | 1.9.23 |
| UI | **Leanback** | androidx.leanback（稳定成熟） |
| 播放器 | **ExoPlayer** | androidx.media3 1.3.x |
| 网络 | **OkHttp + Retrofit + Moshi** | Retrofit 直连 VidHub API |
| 图片 | **Coil** | 2.x |
| 架构 | **MVVM + Repository** | 标准分层 |
| DI | **Hilt** | 2.51 |
| 持久化 | **DataStore / EncryptedSharedPreferences** | 多服务器配置 + 播放历史 + 收藏 |
| 最低 API | **21 (Android 5.0)** | 覆盖 99.9% 电视设备 |
| 编译 SDK | **34** | |

---

## 项目结构

```
VidHub-Android/
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
│   │   │   │   ├── browse/                     # 首页（服务器行 + 继续观看 + 收藏）
│   │   │   │   ├── search/                     # 搜索页（防抖 + 多源聚合）
│   │   │   │   ├── detail/                     # 视频详情 + 剧集列表
│   │   │   │   ├── player/                     # 播放页（ExoPlayer 直连）
│   │   │   │   └── settings/                   # 服务器管理 / 自定义数据源
│   │   │   │
│   │   │   ├── data/
│   │   │   │   ├── remote/
│   │   │   │   │   ├── VidHubApi.kt            # Retrofit 接口
│   │   │   │   │   └── dto/                    # API 响应模型
│   │   │   │   ├── repository/
│   │   │   │   │   └── VideoRepository.kt      # 数据仓库，统一构建 auth URL
│   │   │   │   └── local/
│   │   │   │       ├── ServerConfigStore.kt    # 服务器配置（加密存储）
│   │   │   │       ├── SourcesCacheStore.kt    # 数据源列表缓存
│   │   │   │       ├── WatchHistoryStore.kt    # 播放历史
│   │   │   │       └── FavoritesStore.kt       # 收藏
│   │   │   │
│   │   │   ├── model/                          # ServerConfig / VideoItem / Episode / ...
│   │   │   ├── di/AppModule.kt                 # Hilt 依赖提供
│   │   │   └── util/                           # Sha256 / Constants
│   │   │
│   │   ├── res/                                # 布局 / 主题 / 图标
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
│
├── docs/API.md                                 # 与服务端的 API 约定
├── gradle/wrapper/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

---

## 核心数据流

### 搜索（走 VidHub API，多源聚合）

```
SearchFragment → SearchViewModel（500ms 防抖）
    ↓
VideoRepository.search(server, keyword)
    ↓  并发请求服务器全部数据源（内置源 + 自定义源，最多 6 并发）
    ↓  每个源: {server}/api/search?wd=kw&apiUrl=cmsUrl&auth=sha256(pwd)&t=ts
VidHub 服务端 /api/search 转发到对应 CMS V10 源
    ↓
结果按 stableKey 去重、随源返回渐进上屏
```

### 详情

```
DetailFragment → DetailViewModel.loadDetail(item)
    ↓
VideoRepository.detail(server, item)
    ↓  {server}/api/detail?id=vodId&apiUrl=cmsUrl&auth=sha256(pwd)&t=ts
    ↓  自定义源带"网页详情地址"时自动改走 customDetail 网页抓取模式
返回 { episodes: string[], videoInfo: { title, cover, desc, ... } }
    ↓
DetailFragment 展示视频信息 + 剧集列表
```

### 播放

```
DetailFragment → 点击剧集 → PlaybackActivity
    ↓
ExoPlayer.setMediaItems(directM3u8Urls)   ← 整个剧集列表作为播放队列
    ↓
HLS 直连播放（无代理），自动连播下一集
    ↓
每 10 秒 + 退出时保存播放进度到本地
```

### 多服务器切换

```
首页服务器行 / 设置页
  ├── VidHub 实例 A（选中）→ 搜索/详情/播放走 A
  ├── VidHub 实例 B         → 显示但不用
  └── [+ 添加服务器]        → 输入名称 + URL + 密码（保存前校验密码）
      ↓
切换 = 更新 ActiveServerId（EncryptedSharedPreferences）
      ↓
VideoRepository 读取活跃配置，切换 API 目标地址
```

---

## 与服务端的 API 约定

详见 `docs/API.md`。简要说明：

| 端点 | 方法 | 用途 | 参数 |
|---|---|---|---|
| `{server}/api/search` | GET | 搜索 | wd, apiUrl, pg, auth, t |
| `{server}/api/detail` | GET | 视频详情+剧集 | id, apiUrl, customDetail(可选), auth, t |
| `{server}/api/sources` | GET | 服务器内置数据源列表 | auth, t |
| `{server}/api/env/password` | GET | 获取密码哈希（校验密码） | - |

- `auth = sha256(password)` 十六进制小写；`t` 为毫秒时间戳，服务端容忍 10 分钟误差。
- 搜索/详情走 VidHub API 转发，**视频直连播放**（VidHub 不代理视频流）。

---

## 构建与运行

```bash
# 环境：Android Studio Hedgehog+ / JDK 17 / Android SDK 34
# 克隆后用 Android Studio 打开，Sync Gradle → 连接设备 → Run

# 命令行构建：
./gradlew assembleDebug        # 开发调试包
./gradlew assembleRelease      # 发布包（配置签名环境变量时自动签名）
```

### GitHub Actions 签名构建

仓库已内置 `.github/workflows/build.yml`：push 即构建 release APK 并上传 artifact；打 `v*` 标签自动创建 GitHub Release。

签名通过仓库 Secrets 注入（与旧版一致，已配置过则无需改动）：

| Secret | 说明 |
|---|---|
| `KEYSTORE_BASE64` | keystore 文件的 base64（`base64 -i keystore.jks`） |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥密码 |

未配置 Secrets 时 release 构建自动降级为未签名 APK，不会构建失败。

**首次使用：**
1. 首页 → 「+ 添加服务器」→ 输入名称、VidHub 部署地址、PASSWORD
2. 保存时客户端会通过 `/api/env/password` 校验密码是否正确
3. 返回首页，点左上角搜索球开始搜索

---

## 许可

Apache 2.0（与 VidHub Server 一致）
