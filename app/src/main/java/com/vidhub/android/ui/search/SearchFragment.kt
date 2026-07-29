package com.vidhub.android.ui.search

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vidhub.android.R
import com.vidhub.android.model.VideoItem
import com.vidhub.android.navigation.Router
import com.vidhub.android.ui.browse.TextCard
import com.vidhub.android.ui.browse.TextCardPresenter
import com.vidhub.android.ui.browse.VideoCardPresenter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 搜索页：输入关键词（500ms 防抖）→ 聚合当前服务器所有数据源，结果流式上屏。
 *
 * 防回弹设计（多源结果陆续返回时不重置滚动位置）：
 * 1. 结果行使用 [StableListRowPresenter]，关闭条目动画与焦点对齐；
 * 2. 每批追加后校验网格首可见位置，被重置则修复；
 * 3. 搜索中在结果行下方挂状态行显示"搜索中…已找到 N 部"，完成后切换为
 *    "搜索完成，共找到 N 部"（保留），状态行更新不影响结果行横向滚动。
 */
@AndroidEntryPoint
class SearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val viewModel: SearchViewModel by viewModels()

    private lateinit var stablePresenter: StableListRowPresenter
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var resultsAdapter: ArrayObjectAdapter
    private lateinit var resultsRow: ListRow
    private val resultsHeader = HeaderItem(ROW_RESULTS, "搜索结果")

    /** 搜索状态行（"搜索中…已找到 N 部"），搜索完成后移除 */
    private var statusRow: ListRow? = null
    private var statusAdapter: ArrayObjectAdapter? = null
    private var statusText: String? = null

    /** 当前展示模式：空 / 提示 / 结果列表 */
    private enum class Mode { NONE, HINT, RESULTS }
    private var mode = Mode.NONE

    /** resultsAdapter 中已渲染的条目数（增量追加指针） */
    private var renderedCount = 0
    private var hintText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        stablePresenter = StableListRowPresenter()
        resultsAdapter = ArrayObjectAdapter(VideoCardPresenter())
        stablePresenter.resultsAdapter = resultsAdapter
        rowsAdapter = ArrayObjectAdapter(stablePresenter)

        setSearchResultProvider(this)
        setOnItemViewClickedListener(OnItemViewClickedListener { _, item, _, _ ->
            if (item is VideoItem) Router.openDetail(requireContext(), item)
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeState()
    }

    override fun getResultsAdapter(): ArrayObjectAdapter = rowsAdapter

    override fun onQueryTextChange(newQuery: String): Boolean {
        viewModel.onQueryChanged(newQuery)
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        viewModel.onQuerySubmit(query)
        return true
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: SearchViewModel.SearchUiState) {
        // 新搜索：结果列表被重建（长度回退）→ 清空重来
        if (state.results.size < renderedCount) {
            resultsAdapter.clear()
            renderedCount = 0
        }

        when {
            state.noServer -> showHintOnly(getString(R.string.search_no_server))
            state.authFailed -> showHintOnly(getString(R.string.search_auth_failed))
            state.results.isNotEmpty() -> showStreaming(state)
            state.searching -> showHintOnly("搜索中…")
            state.hasSearched -> showHintOnly(getString(R.string.search_no_result))
            else -> showHintOnly(null)
        }
    }

    /** 流式展示：行结构只建一次，结果增量追加，状态行随搜索进度更新 */
    private fun showStreaming(state: SearchViewModel.SearchUiState) {
        if (mode != Mode.RESULTS) {
            rowsAdapter.clear()
            statusRow = null
            statusAdapter = null
            statusText = null
            if (!::resultsRow.isInitialized) {
                resultsRow = ListRow(resultsHeader, resultsAdapter)
            }
            rowsAdapter.add(resultsRow)
            mode = Mode.RESULTS
            hintText = null
        }

        // 追加前记录首可见位置（用于追加后的回弹校验修复）。
        // 用 RecyclerView 通用 API：最左可见子视图 → 适配器位置。
        val gridView = stablePresenter.resultsGridView
        val prevFirstVisible = gridView?.getChildAt(0)
            ?.let { gridView.getChildAdapterPosition(it) } ?: -1

        while (renderedCount < state.results.size) {
            resultsAdapter.add(state.results[renderedCount])
            renderedCount++
        }

        // 追加后校验：若首可见位置被重置则恢复（正常调优后不会触发，仅兜底）
        if (gridView != null && prevFirstVisible > 0) {
            gridView.post {
                val nowFirstVisible = gridView.getChildAt(0)
                    ?.let { gridView.getChildAdapterPosition(it) } ?: -1
                if (nowFirstVisible != prevFirstVisible) {
                    gridView.scrollToPosition(prevFirstVisible)
                }
            }
        }

        // 状态行维护：搜索中显示实时计数，完成后切换为总计数（保留不消失）
        if (state.searching) {
            updateStatusRow("搜索中… 已找到 ${state.results.size} 部")
        } else {
            updateStatusRow("搜索完成，共找到 ${state.results.size} 部")
        }
    }

    /** 状态行：挂在结果行下方；文本不变时不重建 */
    private fun updateStatusRow(text: String) {
        if (statusRow == null) {
            val adapter = ArrayObjectAdapter(TextCardPresenter())
            adapter.add(TextCard(id = "status", title = text))
            val row = ListRow(HeaderItem(ROW_STATUS, ""), adapter)
            statusAdapter = adapter
            statusRow = row
            statusText = text
            rowsAdapter.add(row)
        } else if (statusText != text) {
            statusAdapter?.clear()
            statusAdapter?.add(TextCard(id = "status", title = text))
            statusText = text
        }
    }

    /** 纯提示模式（无结果网格）；文本不变时不重复重建 */
    private fun showHintOnly(text: String?) {
        if (mode == Mode.HINT && hintText == text) return
        mode = if (text == null) Mode.NONE else Mode.HINT
        hintText = text
        statusRow = null
        statusAdapter = null
        statusText = null
        rowsAdapter.clear()
        if (text != null) {
            val adapter = ArrayObjectAdapter(TextCardPresenter())
            adapter.add(TextCard(id = "hint", title = text))
            rowsAdapter.add(ListRow(HeaderItem(ROW_HINT, ""), adapter))
        }
    }

    companion object {
        private const val ROW_RESULTS = 0L
        private const val ROW_HINT = 1L
        private const val ROW_STATUS = 2L
    }
}
