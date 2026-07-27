package com.libretv.android.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.libretv.android.model.VideoItem

@JsonClass(generateAdapter = true)
data class SearchResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "list") val list: List<VodInfo>?,
    @Json(name = "pagecount") val pagecount: Int?,
    @Json(name = "msg") val msg: String?
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
        playFrom = vodPlayFrom
    )
}
