package com.vidhub.android.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 搜索/浏览结果中的视频条目。
 *
 * 注意：搜索结果不含剧集列表，剧集需通过 /api/detail 获取。
 *
 * @param vodId      CMS 源中的视频 ID
 * @param title      标题
 * @param coverUrl   封面图 URL
 * @param remarks    更新备注（如 "更新至12集"）
 * @param year       年份
 * @param area       地区
 * @param typeName   类型
 * @param description 简介
 * @param director   导演
 * @param actor      主演
 * @param sourceName 来源名称（展示用）
 * @param sourceApi  来源 CMS API 地址（请求详情时必须带上，服务端 apiUrl 参数）
 * @param serverId   所属 VidHub 服务器 ID
 */
@Parcelize
data class VideoItem(
    val vodId: String,
    val title: String,
    val coverUrl: String? = null,
    val remarks: String? = null,
    val year: String? = null,
    val area: String? = null,
    val typeName: String? = null,
    val description: String? = null,
    val director: String? = null,
    val actor: String? = null,
    val sourceName: String = "",
    val sourceApi: String = "",
    val serverId: String = "",
) : Parcelable {

    /** 历史/收藏共用的稳定主键：同一服务器 + 同一源 + 同一视频 */
    val stableKey: String
        get() = "$serverId|$sourceApi|$vodId"
}
