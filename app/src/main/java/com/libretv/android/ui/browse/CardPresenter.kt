package com.libretv.android.ui.browse

import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.libretv.android.R
import com.libretv.android.data.local.WatchHistoryItem
import com.libretv.android.model.VideoItem

class CardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(
                ResourcesCompat.getColor(resources, android.R.color.black, null)
            )
            cardType = ImageCardView.CARD_TYPE_INFO_UNDER
            setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val cardView = viewHolder.view as ImageCardView

        when (item) {
            is VideoItem -> {
                cardView.titleText = item.title
                cardView.contentText = item.remarks ?: item.year ?: ""
                // In production, load image with Coil:
                // CoilImageLoader.load(cardView.context, item.coverUrl, cardView)
            }
            is WatchHistoryItem -> {
                cardView.titleText = item.title
                cardView.contentText = item.sourceName ?: ""
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.badgeImage = null
        cardView.mainImage = null
    }

    companion object {
        private const val CARD_WIDTH = 320
        private const val CARD_HEIGHT = 180
    }
}
