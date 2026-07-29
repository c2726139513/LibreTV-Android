package com.vidhub.android.ui.browse

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.vidhub.android.R

/** 文本卡片数据；flat=true 时渲染为无卡片样式的纯文本（用作行标题） */
data class TextCard(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val payload: Any? = null,
    val flat: Boolean = false,
)

/**
 * 通用文本卡片 Presenter：服务器卡片、功能入口卡片、剧集卡片、行标题等。
 */
class TextCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_card_text, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val card = item as TextCard
        val view = viewHolder.view
        val density = view.resources.displayMetrics.density

        // flat 模式：去卡片背景、不限宽、不可聚焦（纯标题文本）；
        // 视图是复用的，非 flat 时必须恢复卡片样式。
        if (card.flat) {
            view.setBackground(null)
            view.minimumWidth = 0
            view.isFocusable = false
        } else {
            view.setBackgroundResource(R.drawable.card_background)
            view.minimumWidth = (240 * density).toInt()
            view.isFocusable = true
        }

        view.findViewById<TextView>(R.id.card_title).text = card.title
        val subtitleView = view.findViewById<TextView>(R.id.card_subtitle)
        if (card.subtitle.isBlank()) {
            subtitleView.visibility = android.view.View.GONE
        } else {
            subtitleView.visibility = android.view.View.VISIBLE
            subtitleView.text = card.subtitle
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
