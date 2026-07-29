# VidHub Android ↔ 服务端 API 约定

> 本文档依据 VidHub Server 实际实现（Next.js App Router 路由源码）整理。

## 概述

Android 客户端与自由部署的 VidHub 服务端之间的通信协议。
客户端支持**多服务器**，每个服务器独立配置 URL 和密码，可随时切换。

鉴权方式（所有业务接口）：

```
auth = sha256(PASSWORD) 的十六进制小写字符串
t    = 客户端当前毫秒时间戳（服务端容忍 10 分钟误差）
```

- 两个参数都以 **query parameter** 形式出现在 URL 中。
- 鉴权失败返回 **HTTP 401**，响应体 `{ "code": 401, "msg": "未授权访问", ... }`。
- 参数缺失返回 **HTTP 400**。
- 上游 CMS 错误返回 **HTTP 200 + 响应体 code != 200**（客户端必须以响应体 code 为准）。

---

## 1. 搜索 — `GET /api/search`

```
GET {server}/api/search?wd={关键词}&apiUrl={CMS源地址}&pg={页码}&auth={hash}&t={ts}
```

| 参数 | 必填 | 说明 |
|---|---|---|
| `wd` | 是 | 搜索关键词 |
| `apiUrl` | 是* | CMS V10 API 地址（如 `https://example.com/api.php/provide/vod`） |
| `source` | 是* | 源 key（与 apiUrl 二选一；注意：仅传 source 时服务端使用占位地址，**客户端应始终传 apiUrl**） |
| `pg` | 否 | 页码，默认 1 |
| `auth` / `t` | 是 | 鉴权参数 |

**成功响应：**

```json
{
  "code": 200,
  "list": [
    {
      "vod_id": "12345",
      "vod_name": "视频标题",
      "vod_pic": "https://example.com/cover.jpg",
      "vod_remarks": "更新至12集",
      "vod_year": "2024",
      "vod_area": "中国大陆",
      "type_name": "动作片",
      "vod_content": "剧情简介..."
    }
  ],
  "pagecount": 1
}
```

**注意：**
- `list` 为服务端从 CMS 源原样透传的 vod 对象。不同源对 `vod_id`/`vod_year` 的 JSON 类型不统一（可能是数字），客户端需做类型归一化。
- 搜索结果**不含剧集列表**，剧集需通过 `/api/detail` 获取。
- 单源失败不影响其他源（客户端聚合多源搜索）。

---

## 2. 详情 — `GET /api/detail`

```
GET {server}/api/detail?id={视频ID}&apiUrl={CMS源地址}&auth={hash}&t={ts}
GET {server}/api/detail?id={视频ID}&customDetail={网页详情地址}&auth={hash}&t={ts}   （自定义网页源）
```

| 参数 | 必填 | 说明 |
|---|---|---|
| `id` | 是 | 视频 ID（`/^[\w-]+$/`） |
| `apiUrl` | 是* | CMS V10 API 地址（标准模式） |
| `customDetail` | 是* | 网页详情地址（网页抓取模式，二者任一存在即走该模式） |
| `auth` / `t` | 是 | 鉴权参数 |

**成功响应（标准模式）：**

```json
{
  "code": 200,
  "episodes": ["https://cdn.example.com/1.m3u8", "https://cdn.example.com/2.m3u8"],
  "videoInfo": {
    "title": "...", "cover": "...", "desc": "...", "type": "...",
    "year": "...", "area": "...", "director": "...", "actor": "...",
    "remarks": "...", "source_name": "...", "source_code": "..."
  }
}
```

- `episodes` 为**纯 M3U8 播放地址数组**（无集名），客户端按数组序号生成"第N集"。
- 播放地址**直连播放**，VidHub 不代理视频流。
- 网页抓取模式额外返回 `detailUrl` 字段。

---

## 3. 数据源列表 — `GET /api/sources`

```
GET {server}/api/sources?auth={hash}&t={ts}
```

**响应：**

```json
{
  "code": 200,
  "sources": [
    { "key": "dyttzy", "name": "电影天堂资源", "api": "http://caiji.dyttzyapi.com/api.php/provide/vod" }
  ]
}
```

客户端用途：拉取服务器内置源列表用于聚合搜索；建议本地缓存，搜索时无需先拉取。

---

## 4. 密码校验 — `GET /api/env/password`

```
GET {server}/api/env/password
```

无需鉴权。响应：

```json
{ "hash": "sha256(PASSWORD) 十六进制" }
```

- `hash` 为 `null` 表示服务端未配置 `PASSWORD` 环境变量 —— 该实例所有业务接口都会 401，不可用。
- 客户端在添加服务器时比对 `sha256(用户输入密码) == hash` 来校验密码正确性。

---

## 5. 客户端鉴权实现

```kotlin
fun buildVidHubUrl(server: ServerConfig, path: String, params: Map<String, String?>): String {
    val builder = (server.baseUrl + "/" + path).toHttpUrl().newBuilder()
    params.forEach { (k, v) -> v?.let { builder.addQueryParameter(k, it) } }
    builder.addQueryParameter("auth", sha256Hex(server.password))
    builder.addQueryParameter("t", System.currentTimeMillis().toString())
    return builder.build().toString()
}
```

**auth 参数在 URL 中统一构造（VideoRepository.buildVidHubUrl），不使用 OkHttp 拦截器。**

---

## 6. 错误处理约定

| 场景 | 表现 | 客户端处理 |
|---|---|---|
| 密码错误 | HTTP 401 | 中止搜索，提示检查密码 |
| 参数缺失 | HTTP 400 | 视为请求错误（客户端 bug） |
| 单源超时/失败 | HTTP 200 + code=500 或网络异常 | 跳过该源，不影响聚合结果 |
| 视频 ID 不存在 | HTTP 200 + code=404 | 提示"未找到视频详情" |
| 服务端未设 PASSWORD | /api/env/password 返回 hash=null | 添加服务器时阻止并提示 |
