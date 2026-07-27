package com.libretv.android.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DetailResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "episodes") val episodes: List<String>?,
    @Json(name = "videoInfo") val videoInfo: VideoInfo?,
    @Json(name = "detailUrl") val detailUrl: String?,
    @Json(name = "msg") val msg: String?
)

@JsonClass(generateAdapter = true)
data class VideoInfo(
    @Json(name = "title") val title: String?,
    @Json(name = "cover") val cover: String?,
    @Json(name = "desc") val desc: String?,
    @Json(name = "type") val type: String?,
    @Json(name = "year") val year: String?,
    @Json(name = "area") val area: String?,
    @Json(name = "director") val director: String?,
    @Json(name = "actor") val actor: String?,
    @Json(name = "remarks") val remarks: String?,
    @Json(name = "source_name") val sourceName: String?,
    @Json(name = "source_code") val sourceCode: String?
)

@JsonClass(generateAdapter = true)
data class PasswordResponse(
    @Json(name = "hash") val hash: String?
)
