package com.vidhub.android.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SourcesResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "sources") val sources: List<SourceInfo>?,
    @Json(name = "msg") val msg: String?
)

@JsonClass(generateAdapter = true)
data class SourceInfo(
    @Json(name = "key") val key: String,
    @Json(name = "name") val name: String,
    @Json(name = "api") val api: String
)
