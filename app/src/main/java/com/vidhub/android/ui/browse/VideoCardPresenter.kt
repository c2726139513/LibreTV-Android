package com.vidhub.android.ui.browse

import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import coil.load
import com.vidhub.android.R
import com.vidhub.android.model.VideoItem

/**
 * 视频海报卡片（ImageCardView + Coil 加载封面）。
 * 首页继续观看/收藏行、搜索结果、详情页共用。
 */
class VideoCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val density = parent.resources.displayMetrics.density
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(
                (CARD_WIDTH_DP * density).toInt(),
                (CARD_HEIGHT_DP * density).toInt(),
            )
            setBackgroundColor(ContextCompat.getColor(context, R.color.card_background))
            setInfoAreaBackgroundColor(ContextCompat.getColor(context, R.color.surface))
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val video = item as VideoItem
        val cardView = viewHolder.view as ImageCardView

        cardView.titleText = video.title
        cardView.contentText = listOfNotNull(
            video.remarks?.takeIf { it.isNotBlank() },
            video.sourceName.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifEmpty { null }

        // 占位/失败/空地址统一用占位图，由 Coil 一次性处理
        cardView.mainImageView.load(video.coverUrl) {
            placeholder(R.drawable.poster_placeholder)
            error(R.drawable.poster_placeholder)
            fallback(R.drawable.poster_placeholder)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.mainImage = null
    }

    companion object {
        private const val CARD_WIDTH_DP = 180
        private const val CARD_HEIGHT_DP = 254
    }
}
