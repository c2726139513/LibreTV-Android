package com.vidhub.android.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 播放历史条目，本地持久化（DataStore JSON），不依赖服务端。
 *
 * @param key          稳定主键 = serverId|sourceApi|vodId
 * @param vodId        视频 ID
 * @param sourceApi    来源 CMS API 地址（续播请求详情时使用）
 * @param serverId     所属服务器 ID
 * @param title        标题
 * @param coverUrl     封面
 * @param episodeIndex 当前观看集数下标（0 起）
 * @param episodeCount 总集数
 * @param positionMs   播放位置（毫秒）
 * @param durationMs   总时长（毫秒）
 * @param updatedAt    最后观看时间戳
 */
@JsonClass(generateAdapter = true)
data class WatchHistoryItem(
    @Json(name = "key") val key: String,
    @Json(name = "vodId") val vodId: String,
    @Json(name = "sourceApi") val sourceApi: String,
    @Json(name = "serverId") val serverId: String,
    @Json(name = "title") val title: String,
    @Json(name = "coverUrl") val coverUrl: String? = null,
    @Json(name = "episodeIndex") val episodeIndex: Int = 0,
    @Json(name = "episodeCount") val episodeCount: Int = 0,
    @Json(name = "positionMs") val positionMs: Long = 0L,
    @Json(name = "durationMs") val durationMs: Long = 0L,
    @Json(name = "updatedAt") val updatedAt: Long = System.currentTimeMillis(),
)
