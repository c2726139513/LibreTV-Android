package com.vidhub.android.ui.search

import android.os.Bundle
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
 */
@AndroidEntryPoint
class SearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val viewModel: SearchViewModel by viewModels()

    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var resultsAdapter: ArrayObjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        resultsAdapter = ArrayObjectAdapter(VideoCardPresenter())
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        setSearchResultProvider(this)
        setOnItemViewClickedListener(OnItemViewClickedListener { _, item, _, _ ->
            if (item is VideoItem) Router.openDetail(requireContext(), item)
        })
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
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
        resultsAdapter.clear()
        state.results.forEach { resultsAdapter.add(it) }

        rowsAdapter.clear()
        when {
            state.noServer -> rowsAdapter.add(hintRow(getString(R.string.search_no_server)))
            state.authFailed -> rowsAdapter.add(hintRow(getString(R.string.search_auth_failed)))
            state.results.isNotEmpty() -> {
                val suffix = if (state.searching) "…" else ""
                rowsAdapter.add(
                    ListRow(
                        HeaderItem(ROW_RESULTS, "${getString(R.string.app_name)} · ${state.results.size}$suffix"),
                        resultsAdapter,
                    )
                )
            }
            state.hasSearched && !state.searching ->
                rowsAdapter.add(hintRow(getString(R.string.search_no_result)))
        }
    }

    private fun hintRow(text: String): ListRow {
        val adapter = ArrayObjectAdapter(TextCardPresenter())
        adapter.add(TextCard(id = "hint", title = text))
        return ListRow(HeaderItem(ROW_HINT, ""), adapter)
    }

    companion object {
        private const val ROW_RESULTS = 0L
        private const val ROW_HINT = 1L
    }
}
