package com.vidhub.android.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.vidhub.android.model.VideoItem

/**
 * VidHub /api/search 响应：
 * { "code": 200, "list": [ ...CMS V10 vod 对象... ], "pagecount": 1, "msg": "..." }
 *
 * 服务端对上游错误返回 HTTP 200 + code != 200 的包装；鉴权失败返回 HTTP 401。
 */
@JsonClass(generateAdapter = true)
data class SearchResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "list") val list: List<VodInfo>?,
    @Json(name = "pagecount") val pagecount: Int?,
    @Json(name = "msg") val msg: String?,
)

/**
 * CMS V10 vod 对象。
 * 注意：不同 CMS 源对 vod_id / vod_year 的 JSON 类型不统一（可能是数字），
 * 这里用 Any? 接收并做归一化，避免 Moshi 类型不匹配导致整个响应解析失败。
 */
@JsonClass(generateAdapter = true)
data class VodInfo(
    @Json(name = "vod_id") val vodId: Any?,
    @Json(name = "vod_name") val vodName: String?,
    @Json(name = "vod_pic") val vodPic: String?,
    @Json(name = "vod_remarks") val vodRemarks: String?,
    @Json(name = "vod_year") val vodYear: Any?,
    @Json(name = "vod_area") val vodArea: String?,
    @Json(name = "vod_director") val vodDirector: String?,
    @Json(name = "vod_actor") val vodActor: String?,
    @Json(name = "type_name") val typeName: String?,
    @Json(name = "vod_content") val vodContent: String?,
    @Json(name = "vod_play_from") val vodPlayFrom: String?,
    @Json(name = "vod_play_url") val vodPlayUrl: String?,
)

/** 把 Moshi 解析出的任意 JSON 值归一化为 ID 字符串（12345.0 -> "12345"） */
internal fun Any?.asIdString(): String = when (this) {
    null -> ""
    is Double -> if (this % 1.0 == 0.0) toLong().toString() else toString()
    is Float -> if (this % 1.0f == 0.0f) toLong().toString() else toString()
    else -> toString()
}

fun VodInfo.toVideoItem(sourceName: String, sourceApi: String, serverId: String): VideoItem? {
    val id = vodId.asIdString()
    val name = vodName?.takeIf { it.isNotBlank() } ?: return null
    if (id.isBlank()) return null
    return VideoItem(
        vodId = id,
        title = name,
        coverUrl = vodPic?.takeIf { it.isNotBlank() },
        remarks = vodRemarks,
        year = vodYear.asIdString().takeIf { it.isNotBlank() },
        area = vodArea,
        typeName = typeName,
        description = vodContent,
        director = vodDirector,
        actor = vodActor,
        sourceName = sourceName,
        sourceApi = sourceApi,
        serverId = serverId,
    )
}
