package com.vidhub.android.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 收藏条目，本地持久化（DataStore JSON）。
 *
 * @param key       稳定主键 = serverId|sourceApi|vodId
 * @param vodId     视频 ID
 * @param sourceApi 来源 CMS API 地址
 * @param serverId  所属服务器 ID
 * @param title     标题
 * @param coverUrl  封面
 * @param remarks   更新备注
 * @param addedAt   收藏时间戳
 */
@JsonClass(generateAdapter = true)
data class FavoriteItem(
    @Json(name = "key") val key: String,
    @Json(name = "vodId") val vodId: String,
    @Json(name = "sourceApi") val sourceApi: String,
    @Json(name = "serverId") val serverId: String,
    @Json(name = "title") val title: String,
    @Json(name = "coverUrl") val coverUrl: String? = null,
    @Json(name = "remarks") val remarks: String? = null,
    @Json(name = "addedAt") val addedAt: Long = System.currentTimeMillis(),
)
