package com.vidhub.android.ui.browse

import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.vidhub.android.R
import com.vidhub.android.data.local.WatchHistoryItem
import com.vidhub.android.model.Episode
import com.vidhub.android.model.SettingsAction
import com.vidhub.android.model.VideoItem

class CardPresenter(
    private val onItemClickListener: ((Any) -> Unit)? = null
) : Presenter() {

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
            }
            is WatchHistoryItem -> {
                cardView.titleText = item.title
                cardView.contentText = item.sourceName ?: ""
            }
            is Episode -> {
                cardView.titleText = item.name
                cardView.contentText = item.url
            }
            is SettingsAction -> {
                cardView.titleText = SettingsAction.LABEL
                cardView.contentText = SettingsAction.DESCRIPTION
            }
        }

        cardView.setOnClickListener { onItemClickListener?.invoke(item) }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.badgeImage = null
        cardView.mainImage = null
        cardView.setOnClickListener(null)
    }

    companion object {
        private const val CARD_WIDTH = 320
        private const val CARD_HEIGHT = 180
    }
}
