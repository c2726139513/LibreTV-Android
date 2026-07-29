package com.vidhub.android.ui.search

import android.view.ViewGroup
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.RowPresenter

/**
 * 搜索结果行专用 ListRowPresenter。
 *
 * 解决"流式追加结果时网格回弹到最前面"的问题：
 * 1. 关闭行内网格的条目变更动画 —— 插入新卡片时不触发动画布局；
 * 2. 关闭焦点条目对齐（item alignment）—— 布局时不再把焦点卡片"吸"回对齐位置。
 *
 * 同时把结果行的网格视图暴露给 Fragment，用于追加后的滚动位置校验修复。
 */
class StableListRowPresenter : ListRowPresenter() {

    /** Fragment 设置：结果行的内容适配器（用于识别哪个网格是结果网格） */
    var resultsAdapter: ArrayObjectAdapter? = null

    /** 结果行的网格视图（绑定时捕获） */
    var resultsGridView: HorizontalGridView? = null
        private set

    override fun createRowViewHolder(parent: ViewGroup): RowPresenter.ViewHolder {
        val holder = super.createRowViewHolder(parent)
        (holder as? ViewHolder)?.gridView?.let { tune(it) }
        return holder
    }

    override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder, item: Any) {
        super.onBindRowViewHolder(holder, item)
        val row = item as? ListRow ?: return
        if (row.adapter === resultsAdapter) {
            resultsGridView = (holder as? ViewHolder)?.gridView
        }
    }

    private fun tune(gridView: HorizontalGridView) {
        // 关闭条目变更动画
        gridView.itemAnimator = null
        // 关闭焦点条目对齐（滚动改为"最小滚动保可见"，布局不再拉动位置）
        gridView.setItemAlignmentOffsetPercent(BaseGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED)
    }
}
