package com.libretv.android.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ServerConfig(
    @Json(name = "id") val id: String = java.util.UUID.randomUUID().toString(),
    @Json(name = "name") val name: String,
    @Json(name = "url") val url: String,
    @Json(name = "password") val password: String,
    @Json(name = "isActive") val isActive: Boolean = false,
    @Json(name = "cmsSources") val cmsSources: List<String> = emptyList(),
    @Json(name = "addedAt") val addedAt: Long = System.currentTimeMillis()
)
