package com.vidhub.android.ui.search

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
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
 * 结构（行 header 已被 [StableListRowPresenter] 隐藏）：
 *   [标题行]  "搜索结果（搜索中…已找到 N 部）" → 完成后 "搜索结果（搜索完成，共找到 N 部）"
 *   [结果行]  视频卡片，流式增量追加
 *
 * 防回弹：结果网格关闭条目动画、焦点对齐，焦点滚动策略为最小滚动（见 StableListRowPresenter），
 * 图片加载/数据追加触发的布局不会拉动滚动位置。
 */
@AndroidEntryPoint
class SearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val viewModel: SearchViewModel by viewModels()

    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var resultsAdapter: ArrayObjectAdapter
    private lateinit var resultsRow: ListRow

    /** 标题行（"搜索结果（…）"），位于结果行上方，扁平文本不可聚焦 */
    private var titleAdapter: ArrayObjectAdapter? = null
    private var titleText: String? = null

    /** 当前展示模式：空 / 提示 / 结果列表 */
    private enum class Mode { NONE, HINT, RESULTS }
    private var mode = Mode.NONE

    /** resultsAdapter 中已渲染的条目数（增量追加指针） */
    private var renderedCount = 0
    private var hintText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        resultsAdapter = ArrayObjectAdapter(VideoCardPresenter())
        rowsAdapter = ArrayObjectAdapter(StableListRowPresenter())

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

    /** 流式展示：行结构只建一次，结果增量追加，标题行随进度更新 */
    private fun showStreaming(state: SearchViewModel.SearchUiState) {
        if (mode != Mode.RESULTS) {
            rowsAdapter.clear()
            if (!::resultsRow.isInitialized) {
                resultsRow = ListRow(resultsAdapter)
            }
            // 标题行在上，结果行在下
            rowsAdapter.add(ListRow(buildTitleAdapter(titleOf(state))))
            rowsAdapter.add(resultsRow)
            mode = Mode.RESULTS
            hintText = null
        } else {
            // 标题文本随进度更新（搜索中…已找到 N 部 → 搜索完成，共找到 N 部）
            val newTitle = titleOf(state)
            if (titleText != newTitle) {
                titleAdapter?.clear()
                titleAdapter?.add(TextCard(id = "title", title = newTitle, flat = true))
                titleText = newTitle
            }
        }

        // 结果增量追加（StableListRowPresenter 已关闭动画与对齐，插入无副作用）
        while (renderedCount < state.results.size) {
            resultsAdapter.add(state.results[renderedCount])
            renderedCount++
        }
    }

    private fun titleOf(state: SearchViewModel.SearchUiState): String {
        return if (state.searching) {
            "搜索结果（搜索中… 已找到 ${state.results.size} 部）"
        } else {
            "搜索结果（搜索完成，共找到 ${state.results.size} 部）"
        }
    }

    private fun buildTitleAdapter(text: String): ArrayObjectAdapter {
        val adapter = ArrayObjectAdapter(TextCardPresenter())
        adapter.add(TextCard(id = "title", title = text, flat = true))
        titleAdapter = adapter
        titleText = text
        return adapter
    }

    /** 纯提示模式（无结果网格）；文本不变时不重复重建 */
    private fun showHintOnly(text: String?) {
        if (mode == Mode.HINT && hintText == text) return
        mode = if (text == null) Mode.NONE else Mode.HINT
        hintText = text
        titleAdapter = null
        titleText = null
        rowsAdapter.clear()
        if (text != null) {
            val adapter = ArrayObjectAdapter(TextCardPresenter())
            adapter.add(TextCard(id = "hint", title = text))
            rowsAdapter.add(ListRow(adapter))
        }
    }
}
