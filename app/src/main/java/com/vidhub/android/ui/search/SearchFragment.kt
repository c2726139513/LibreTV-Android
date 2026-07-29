package com.vidhub.android.ui.search

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
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
 * 搜索页：输入关键词（500ms 防抖）→ 聚合当前服务器所有数据源的结果。
 *
 * 渲染采用增量追加：多源结果陆续返回时只 append 新条目，
 * 不重建适配器，避免滚动/焦点位置被重置回第一项。
 */
@AndroidEntryPoint
class SearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val viewModel: SearchViewModel by viewModels()

    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var resultsAdapter: ArrayObjectAdapter
    private lateinit var resultsRow: ListRow
    private val resultsHeader = HeaderItem(ROW_RESULTS, "")

    /** 当前展示模式：空 / 提示 / 结果列表 */
    private enum class Mode { NONE, HINT, RESULTS }
    private var mode = Mode.NONE

    /** resultsAdapter 中已渲染的条目数（增量追加指针） */
    private var renderedCount = 0
    private var hintText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        resultsAdapter = ArrayObjectAdapter(VideoCardPresenter())
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
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
            state.noServer -> showHint(getString(R.string.search_no_server))
            state.authFailed -> showHint(getString(R.string.search_auth_failed))
            state.results.isNotEmpty() -> showResults(state)
            state.hasSearched && !state.searching -> showHint(getString(R.string.search_no_result))
            else -> showHint(null)
        }
    }

    /** 结果模式：行结构只建一次，后续仅增量追加 + 更新表头计数 */
    private fun showResults(state: SearchViewModel.SearchUiState) {
        if (mode != Mode.RESULTS) {
            rowsAdapter.clear()
            if (!::resultsRow.isInitialized) {
                resultsRow = ListRow(resultsHeader, resultsAdapter)
            }
            rowsAdapter.add(resultsRow)
            mode = Mode.RESULTS
            hintText = null
        }

        // 只追加新到达的条目，已有条目与滚动位置不动
        while (renderedCount < state.results.size) {
            resultsAdapter.add(state.results[renderedCount])
            renderedCount++
        }

        val suffix = if (state.searching) "…" else ""
        val newName = "搜索结果（${state.results.size}）$suffix"
        if (resultsHeader.name != newName) {
            resultsHeader.name = newName
            val index = rowsAdapter.indexOf(resultsRow)
            if (index >= 0) rowsAdapter.notifyItemRangeChanged(index, 1)
        }
    }

    /** 提示模式（或无内容）：提示文本不变时不重复重建 */
    private fun showHint(text: String?) {
        if (text == null) {
            if (mode != Mode.NONE) {
                mode = Mode.NONE
                hintText = null
                rowsAdapter.clear()
            }
            return
        }
        if (mode == Mode.HINT && hintText == text) return
        mode = Mode.HINT
        hintText = text
        rowsAdapter.clear()
        val adapter = ArrayObjectAdapter(TextCardPresenter())
        adapter.add(TextCard(id = "hint", title = text))
        rowsAdapter.add(ListRow(HeaderItem(ROW_HINT, ""), adapter))
    }

    companion object {
        private const val ROW_RESULTS = 0L
        private const val ROW_HINT = 1L
    }
}
