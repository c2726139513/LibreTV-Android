# LibreTV Android ↔ 服务端 API 约定

## 概述

Android 客户端与自由部署的 LibreTV 服务端之间的通信协议。

客户端支持**多服务器**，每个服务器独立配置 URL 和密码，可随时切换。

---

## 1. 视频代理 — `/proxy/`

所有视频播放请求必须经过此端点。

### 请求

```
GET {server_url}/proxy/{encodedTargetUrl}?auth={sha256_hash}&t={timestamp}
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `encodedTargetUrl` | path | 是 | 原始视频 URL 的 **URI 编码** (encodeURIComponent) |
| `auth` | query | 是 | `sha256(password)` 十六进制小写字符串 |
| `t` | query | 是 | 当前时间戳 `Date.now()`，用于防重放 |

### 响应

| 状态码 | 说明 |
|---|---|
| 200 | 正常返回视频流或处理后的 M3U8 |
| 401 | 鉴权失败 (密码不匹配) |
| 400 | 无效的代理路径 |
| 500 | 代理内部错误 |

**Content-Type：**
- M3U8 流: `application/vnd.apple.mpegurl; charset=utf-8`
- 其他: 透传上游 Content-Type

### 客户端实现

ExoPlayer 的 `ProxyDataSource` 自动处理 URL 改写：

```
原始 URL:  https://cdn.example.com/path/segment.ts
代理 URL:  https://libretv-server.com/proxy/https%3A%2F%2Fcdn.example.com%2Fpath%2Fsegment.ts?auth=abc123...&t=1712345678901
```

```
原始 M3U8:  https://cdn.example.com/playlist.m3u8
代理 URL:   https://libretv-server.com/proxy/https%3A%2F%2Fcdn.example.com%2Fplaylist.m3u8?auth=abc123...&t=1712345678901
```

**注意：** ExoPlayer 遇到 Master M3U8 时，会解析内部分片 URL 并逐个请求。这些内部分片需要**同样走代理**。有两种方式：

1. **方案 A（推荐）：** 客户端将所有传给 ExoPlayer 的 URL 都预先包上 `/proxy/` 前缀。服务端返回已改写的 M3U8（现有 LibreTV 服务端已内置 M3U8 改写逻辑）。客户端无需关心内部 URL。
2. **方案 B：** 客户端只把入口 M3U8 包上 `/proxy/`，内部分片 URL 由 ExoPlayer 直接请求（会绕过代理）。不推荐。

**实现方式 A 的流程：**

```
PlaybackActivity
  ├── 构造入口 M3U8 的代理 URL
  │   /proxy/https%3A%2F%2Forigin.com%2Fmaster.m3u8?auth=xxx&t=xxx
  │
  ├── ExoPlayer setMediaItem(proxyUrl)
  │
  └── ExoPlayer 请求 master.m3u8
      ↓ (返回已改写的内容)
      服务端解析 master.m3u8 → 选择最佳码率流 → 获取 media.m3u8 → 改写内部分片 URL
      ↓
      media.m3u8 中所有分片已变为 /proxy/ 路径
      ↓
      ExoPlayer 请求每个分片 → 同样走 /proxy/ (已自带 auth? 需要检查)
      ↓
      如果服务端已在 M3U8 中嵌入了带 auth 参数的 /proxy/ URL → 完美
      如果服务端只在 M3U8 中嵌了 /proxy/URL 但不含 auth → 客户端需补上 auth
```

**关于 auth 参数的位置：**

查看现有 LibreTV 服务端的 `rewriteUrlToProxy` 实现：

```javascript
function rewriteUrlToProxy(targetUrl) {
    return `/proxy/${encodeURIComponent(targetUrl)}`;  // 路径中不包含 auth
}
```

服务端返回的 M3U8 中，分片 URL 会被改写成 `/proxy/https%3A%2F%2F...` 的路径格式，但**不包含 auth 参数**。

所以客户端有两个选择：

1. **通过 OkHttp Interceptor 统一注入** — 拦截所有发往 `*/proxy/*` 的请求，自动添加 `auth` 和 `t` 参数
2. **在 ProxyDataSource 中注入** — 在 DataSource 层面处理

**推荐的客户端实现（方案 A + Interceptor）：**

```
ExoPlayer 请求:
  /proxy/https%3A%2F%2Forigin.com%2Fsegment.ts   ← 服务端返回的 M3U8 中已改写
      ↓
OkHttp ProxyInterceptor:
  /proxy/https%3A%2F%2Forigin.com%2Fsegment.ts?auth=xxx&t=xxx
      ↓
LibreTV Server: 验证 auth → 请求 origin → 返回内容
```

---

## 2. 搜索 / 详情 — 通过服务端代理

搜索和详情请求**复用视频代理的 `/proxy/` 通道**。客户端将 CMS V10 URL 编码后，作为 `/proxy/` 的 path 发送到自己的 LibreTV 服务端，服务端解码后转发到第三方 CMS 源。

这样做的好处：
- 隐藏上游 CMS 源地址，用户不知道后端接入了哪些源
- 统一鉴权（所有请求都要过 auth）
- 避免客户端 IP 被第三方源屏蔽

### 请求格式

```
GET {server_url}/proxy/{encodedCmsUrl}?auth={sha256_hash}&t={timestamp}
```

| 参数 | 类型 | 说明 |
|---|---|---|
| `encodedCmsUrl` | path | CMS V10 API URL 的完整 URI 编码 |
| `auth` | query | `sha256(password)` |
| `t` | query | 时间戳 |

### 示例

搜索 "黑客帝国":

```
服务器地址:     https://libretv.example.com
CMS 源地址:    https://cms.example.com/api.php/provide/vod?ac=videolist&wd=黑客帝国

完整请求:
GET https://libretv.example.com/proxy/https%3A%2F%2Fcms.example.com%2Fapi.php%2Fprovide%2Fvod%3Fac%3Dvideolist%26wd%3D%E9%BB%91%E5%AE%A2%E5%B8%9D%E5%9B%BD?auth=abc123...&t=1712345678901
```

详情:

```
CMS 详情 URL:  https://cms.example.com/api.php/provide/vod?ac=videolist&ids=12345

完整请求:
GET https://libretv.example.com/proxy/https%3A%2F%2Fcms.example.com%2Fapi.php%2Fprovide%2Fvod%3Fac%3Dvideolist%26ids%3D12345?auth=abc123...&t=1712345678901
```

### 响应

CMS V10 标准 JSON 响应，与直连时完全一致：

```json
{
    "code": 1,
    "list": [
        {
            "vod_id": "12345",
            "vod_name": "视频标题",
            "vod_pic": "https://example.com/cover.jpg",
            "vod_remarks": "更新至第12集",
            "vod_year": "2024",
            "vod_area": "中国大陆",
            "vod_director": "导演名",
            "vod_actor": "演员1,演员2",
            "type_name": "动作片",
            "vod_content": "剧情简介...",
            "vod_play_from": "源名称",
            "vod_play_url": "第1集$https://cdn.example.com/1.m3u8#第2集$https://cdn.example.com/2.m3u8"
        }
    ],
    "page": 1,
    "pagecount": 1,
    "total": 1,
    "limit": 20
}
```

### 客户端 DTO

```kotlin
@JsonClass(generateAdapter = true)
data class SearchResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "list") val list: List<VodInfo>?,
    @Json(name = "page") val page: Int?,
    @Json(name = "pagecount") val pagecount: Int?,
    @Json(name = "total") val total: Int?,
    @Json(name = "limit") val limit: Int?,
)

@JsonClass(generateAdapter = true)
data class VodInfo(
    @Json(name = "vod_id") val vodId: String,
    @Json(name = "vod_name") val vodName: String,
    @Json(name = "vod_pic") val vodPic: String?,
    @Json(name = "vod_remarks") val vodRemarks: String?,
    @Json(name = "vod_year") val vodYear: String?,
    @Json(name = "vod_area") val vodArea: String?,
    @Json(name = "vod_director") val vodDirector: String?,
    @Json(name = "vod_actor") val vodActor: String?,
    @Json(name = "type_name") val typeName: String?,
    @Json(name = "vod_content") val vodContent: String?,
    @Json(name = "vod_play_from") val vodPlayFrom: String?,
    @Json(name = "vod_play_url") val vodPlayUrl: String?,
)
```

### 解析剧集

`vod_play_url` 格式: `第1集$https://cdn.example.com/1.m3u8#第2集$https://cdn.example.com/2.m3u8`

```kotlin
fun parseEpisodes(playUrl: String): List<Episode> {
    return playUrl.split("#").mapNotNull { segment ->
        val parts = segment.split("$", limit = 2)
        if (parts.size == 2) {
            Episode(name = parts[0], url = parts[1])
        } else null
    }
}
```

### 客户端构造代理 URL 的工具函数

```kotlin
fun buildProxyUrl(serverUrl: String, targetUrl: String, password: String): String {
    val encoded = Uri.encode(targetUrl)
    val timestamp = System.currentTimeMillis()
    val hash = sha256(password)
    val base = serverUrl.trimEnd('/')
    return "$base/proxy/$encoded?auth=$hash&t=$timestamp"
}

// 使用示例:
// val searchUrl = buildProxyUrl(
//     serverUrl = "https://libretv.example.com",
//     targetUrl = "https://cms.example.com/api.php/provide/vod?ac=videolist&wd=黑客帝国",
//     password = "mypassword"
// )
// → https://libretv.example.com/proxy/https%3A%2F%2Fcms.example.com%2F...?auth=xxx&t=xxx
```

---

## 3. 健康检查

用于验证服务器地址和密码是否有效。

### 方式一：搜索空请求

```
GET {server_url}/api/env/index
```

通过 HTTP 返回码判断：
- 200 → 服务端可访问
- 其他 → 不可达

### 方式二：代理自身

```
GET {server_url}/proxy/https%3A%2F%2Fexample.com%2Fhealth?auth={hash}&t={ts}
```

返回 401 = 密码错误，返回 400 = 可达但无目标（可以接受，说明服务端正常）

---

## 4. 鉴权计算

```kotlin
fun sha256(input: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    return digest.digest(input.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
```

与服务端保持一致：`sha256(PASSWORD)` 的十六进制小写字符串。

---

## 5. 服务器配置格式

```kotlin
data class ServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,                    // 用户自定义名称
    val url: String,                     // 例: https://libretv.example.com
    val password: String,                // 原始密码 (加密存储)
    val isActive: Boolean = false,       // 是否当前选中
    val cmsSources: List<String> = emptyList(),  // CMS API 地址列表
    val addedAt: Long = System.currentTimeMillis(),
)
```

CMS V10 源地址由用户手动输入（格式: `https://domain.com/api.php/provide/vod`），与服务地址分开存储。

**搜索流程：** 用户选中一个服务器 → 输入搜索关键词 → 客户端遍历该服务器的 `cmsSources` → 对每个源构造 `{serverUrl}/proxy/{encodedCmsUrl}` → 请求经服务端转发 → 聚合结果后展示。

如果未配置 CMS 源，客户端使用服务端默认源列表（服务端内置源，由服务器管理员配置）。
