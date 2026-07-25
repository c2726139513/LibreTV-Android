package com.libretv.android.ui.search

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.libretv.android.R
import com.libretv.android.model.VideoItem
import com.libretv.android.navigation.Router
import com.libretv.android.ui.browse.CardPresenter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : SearchSupportFragment() {

    private val viewModel: SearchViewModel by viewModels()
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setSearchIcon(null)
        setBadgeDrawable(null)
        setTitle(getString(R.string.search_title))
        setOnItemViewClickedListener { itemViewHolder, item, rowViewHolder, row ->
            if (item is VideoItem) {
                Router.navigateToDetail(requireActivity(), item.vodId, item.title, item.coverUrl)
            }
        }

        // Set up search results adapter
        setAdapter(rowsAdapter)

        // Handle search queries
        setOnQueryTextListener(object : SearchSupportFragment.OnQueryTextListener {
            override fun onQueryTextChange(newQuery: String?): Boolean {
                newQuery?.let { viewModel.search(it) }
                return true
            }

            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { viewModel.search(it) }
                return true
            }
        })
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.searchResults.collect { results ->
                        updateResults(results)
                    }
                }
                launch {
                    viewModel.isSearching.collect { searching ->
                        // Could show/hide a loading indicator
                    }
                }
                launch {
                    viewModel.error.collect { errorMsg ->
                        errorMsg?.let {
                            // Show error toast or indicator
                        }
                    }
                }
            }
        }
    }

    private fun updateResults(results: List<VideoItem>) {
        rowsAdapter.clear()
        if (results.isNotEmpty()) {
            val header = HeaderItem("results", "搜索结果")
            val cardPresenter = CardPresenter()
            val adapter = ArrayObjectAdapter(cardPresenter)
            for (item in results) {
                adapter.add(item)
            }
            rowsAdapter.add(ListRow(header, adapter))
        }
    }
}
