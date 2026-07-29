package com.vidhub.android.ui.detail

import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import com.vidhub.android.model.VideoItem

/**
 * 详情页文字区：标题 / 副标题（年份·地区·类型·来源）/ 正文（导演·主演·简介）。
 */
class DetailsDescriptionPresenter : AbstractDetailsDescriptionPresenter() {

    override fun onBindDescription(viewHolder: ViewHolder, item: Any) {
        val video = item as VideoItem

        viewHolder.title.text = video.title

        viewHolder.subtitle.text = listOfNotNull(
            video.year?.takeIf { it.isNotBlank() },
            video.area?.takeIf { it.isNotBlank() },
            video.typeName?.takeIf { it.isNotBlank() },
            video.remarks?.takeIf { it.isNotBlank() },
            video.sourceName.takeIf { it.isNotBlank() },
        ).joinToString(" · ")

        viewHolder.body.text = buildString {
            video.director?.takeIf { it.isNotBlank() }?.let { append("导演：$it\n") }
            video.actor?.takeIf { it.isNotBlank() }?.let { append("主演：$it\n\n") }
            append(video.description?.trim().orEmpty())
        }.trim()
    }
}
