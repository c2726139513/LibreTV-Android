package com.vidhub.android.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 剧集。VidHub /api/detail 返回纯 URL 列表，集数名由客户端按序号生成。
 */
@Parcelize
data class Episode(
    val index: Int,
    val name: String,
    val url: String,
) : Parcelable {

    companion object {
        /** 将详情 API 返回的 URL 列表转成剧集列表，命名为 "第N集" */
        fun fromUrls(urls: List<String>): List<Episode> =
            urls.mapIndexed { index, url ->
                Episode(index = index, name = "第${index + 1}集", url = url)
            }
    }
}
