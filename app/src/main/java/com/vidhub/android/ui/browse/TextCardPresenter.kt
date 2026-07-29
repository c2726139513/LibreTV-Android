package com.vidhub.android.ui.browse

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.vidhub.android.R

/** 文本卡片数据 */
data class TextCard(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val payload: Any? = null,
)

/**
 * 通用文本卡片 Presenter：服务器卡片、功能入口卡片、剧集卡片等。
 */
class TextCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_card_text, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val card = item as TextCard
        viewHolder.view.findViewById<TextView>(R.id.card_title).text = card.title
        val subtitleView = viewHolder.view.findViewById<TextView>(R.id.card_subtitle)
        if (card.subtitle.isBlank()) {
            subtitleView.visibility = android.view.View.GONE
        } else {
            subtitleView.visibility = android.view.View.VISIBLE
            subtitleView.text = card.subtitle
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
