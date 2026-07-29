package com.vidhub.android.ui.search

import android.view.ViewGroup
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.RowPresenter

/**
 * 搜索结果行专用 ListRowPresenter。
 *
 * 解决"图片/数据刷新导致网格回弹"的问题，三管齐下：
 * 1. 关闭条目变更动画 —— 插入新卡片时不触发动画布局；
 * 2. 关闭焦点条目对齐（itemAlignment）；
 * 3. 焦点滚动策略改为 FOCUS_SCROLL_ITEM（最小滚动保可见）——
 *    默认的 FOCUS_SCROLL_ALIGNED 会在每次布局（包括图片加载触发的布局）
 *    把焦点卡片重新对齐到边缘，是回弹的总开关。
 *
 * 另外通过 setHeaderPresenter(null) 隐藏行 header，
 * 页面标题由独立的扁平标题行实现（见 SearchFragment）。
 */
class StableListRowPresenter : ListRowPresenter() {

    init {
        setHeaderPresenter(null)
    }

    override fun createRowViewHolder(parent: ViewGroup): RowPresenter.ViewHolder {
        val holder = super.createRowViewHolder(parent)
        (holder as? ViewHolder)?.gridView?.let { tune(it) }
        return holder
    }

    override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder, item: Any) {
        super.onBindRowViewHolder(holder, item)
        // 绑定时再次调优，确保覆盖默认配置
        (holder as? ViewHolder)?.gridView?.let { tune(it) }
    }

    private fun tune(gridView: HorizontalGridView) {
        // 关闭条目变更动画
        gridView.itemAnimator = null
        // 关闭焦点条目对齐
        gridView.setItemAlignmentOffsetPercent(BaseGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED)
        // 最小滚动策略：布局时不再拉动焦点卡片位置
        gridView.setFocusScrollStrategy(BaseGridView.FOCUS_SCROLL_ITEM)
    }
}
