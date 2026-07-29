package com.vidhub.android.model

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

/**
 * CMS 数据源。
 * 内置源来自 VidHub 服务端 /api/sources；自定义源由用户手动添加。
 *
 * @param key      源标识（内置源为服务端 key，自定义源为 "custom_<id>"）
 * @param name     源名称
 * @param api      CMS V10 API 地址（如 https://example.com/api.php/provide/vod）
 * @param isCustom 是否用户自定义源
 * @param detailUrl 自定义源的网页详情地址（对应服务端 customDetail 参数），仅自定义源可用
 */
@JsonClass(generateAdapter = true)
@Parcelize
data class ApiSource(
    @Json(name = "key") val key: String,
    @Json(name = "name") val name: String,
    @Json(name = "api") val api: String,
    @Json(name = "isCustom") val isCustom: Boolean = false,
    @Json(name = "detailUrl") val detailUrl: String? = null,
) : Parcelable
