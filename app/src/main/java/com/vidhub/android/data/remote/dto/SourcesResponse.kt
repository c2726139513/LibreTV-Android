package com.vidhub.android.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * VidHub /api/sources 响应：
 * { "code": 200, "sources": [ { "key": "...", "name": "...", "api": "..." } ], "msg": "..." }
 */
@JsonClass(generateAdapter = true)
data class SourcesResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "sources") val sources: List<SourceInfo>?,
    @Json(name = "msg") val msg: String?,
)

@JsonClass(generateAdapter = true)
data class SourceInfo(
    @Json(name = "key") val key: String,
    @Json(name = "name") val name: String,
    @Json(name = "api") val api: String,
)

/**
 * VidHub /api/env/password 响应：{ "hash": "sha256hex" | null }
 * 用于客户端本地校验输入的服务器密码是否正确。
 */
@JsonClass(generateAdapter = true)
data class PasswordResponse(
    @Json(name = "hash") val hash: String?,
)
