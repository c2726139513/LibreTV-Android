package com.vidhub.android.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * VidHub 服务器配置。
 * 每个服务器是一个独立的 VidHub 部署实例，独立存储 URL 与密码（密码加密存储）。
 */
@JsonClass(generateAdapter = true)
data class ServerConfig(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "url") val url: String,
    @Json(name = "password") val password: String,
    @Json(name = "addedAt") val addedAt: Long = System.currentTimeMillis(),
) {
    /** 规范化后的服务器地址（去掉末尾斜杠） */
    val baseUrl: String
        get() = url.trimEnd('/')
}
