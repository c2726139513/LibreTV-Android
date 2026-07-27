package com.vidhub.android.ui.detail

import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import com.vidhub.android.model.VideoItem

class DetailsDescriptionPresenter : AbstractDetailsDescriptionPresenter() {

    override fun onBindDescription(viewHolder: ViewHolder, item: Any) {
        when (item) {
            is VideoItem -> {
                viewHolder.title.text = item.title

                val subtitle = buildString {
                    item.year?.let { append("$it ") }
                    item.area?.let { append(" | $it") }
                    item.typeName?.let { append(" | $it") }
                    item.remarks?.let { append(" | $it") }
                }
                viewHolder.subtitle.text = subtitle.trim()

                val body = buildString {
                    item.director?.let { append("导演: $it\n") }
                    item.actor?.let { append("主演: $it\n") }
                    item.description?.let { append("\n$it") }
                    item.playFrom?.let { append("\n\n来源: $it") }
                }
                viewHolder.body.text = body.trim()
            }
        }
    }
}
