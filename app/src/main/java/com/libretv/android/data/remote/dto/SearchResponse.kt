package com.libretv.android.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.libretv.android.model.VideoItem
import com.libretv.android.model.Episode

@JsonClass(generateAdapter = true)
data class SearchResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "list") val list: List<VodInfo>?,
    @Json(name = "page") val page: Int?,
    @Json(name = "pagecount") val pagecount: Int?,
    @Json(name = "total") val total: Int?,
    @Json(name = "limit") val limit: Int?
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
    @Json(name = "vod_play_url") val vodPlayUrl: String?
)

fun VodInfo.toVideoItem(): VideoItem {
    val episodes = vodPlayUrl?.let { parseEpisodes(it) } ?: emptyList()
    return VideoItem(
        vodId = vodId,
        title = vodName,
        coverUrl = vodPic,
        remarks = vodRemarks,
        year = vodYear,
        area = vodArea,
        director = vodDirector,
        actor = vodActor,
        typeName = typeName,
        description = vodContent,
        playFrom = vodPlayFrom,
        episodes = episodes
    )
}

fun parseEpisodes(playUrl: String): List<Episode> {
    return playUrl.split("#").mapIndexedNotNull { index, segment ->
        val parts = segment.split("$", limit = 2)
        if (parts.size == 2) {
            Episode(name = parts[0], url = parts[1], index = index)
        } else null
    }
}
