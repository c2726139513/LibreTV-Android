package com.vidhub.android.ui.search

import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.RowPresenter

/**
 * 搜索结果行专用 ListRowPresenter。
 *
 * 防回弹（根因修复）：焦点滚动策略改为 FOCUS_SCROLL_ITEM（最小滚动保可见）。
 * 默认的 FOCUS_SCROLL_ALIGNED 会在每次布局（包括图片加载、追加数据触发的布局）
 * 把焦点卡片重新对齐到边缘，导致"弹回前面"。条目动画一并关闭，避免流式插入时的闪烁。
 *
 * 同时通过 setHeaderPresenter(null) 隐藏行 header——
 * 页面标题由独立的扁平标题行实现（见 SearchFragment）。
 */
class StableListRowPresenter : ListRowPresenter() {

    init {
        setHeaderPresenter(null)
    }

    override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder, item: Any) {
        super.onBindRowViewHolder(holder, item)
        (holder as? ViewHolder)?.gridView?.let { gridView ->
            gridView.itemAnimator = null
            gridView.setFocusScrollStrategy(BaseGridView.FOCUS_SCROLL_ITEM)
        }
    }
}
