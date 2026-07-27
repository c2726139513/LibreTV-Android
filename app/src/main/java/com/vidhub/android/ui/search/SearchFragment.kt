package com.vidhub.android.ui.search

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ItemBridgeAdapter
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vidhub.android.R
import com.vidhub.android.model.VideoItem
import com.vidhub.android.navigation.Router
import com.vidhub.android.ui.browse.CardPresenter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : Fragment(R.layout.search_fragment) {

    private val viewModel: SearchViewModel by viewModels()
    private lateinit var searchInput: EditText
    private lateinit var searchButton: Button
    private lateinit var resultsGrid: VerticalGridView
    private lateinit var emptyText: TextView
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchInput = view.findViewById(R.id.search_input)
        searchButton = view.findViewById(R.id.search_button)
        resultsGrid = view.findViewById(R.id.search_results)
        emptyText = view.findViewById(R.id.search_empty)

        resultsGrid.adapter = ItemBridgeAdapter(rowsAdapter)

        val doSearch = {
            val q = searchInput.text.toString()
            if (q.isNotBlank()) viewModel.search(q)
        }

        searchButton.setOnClickListener { doSearch() }

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch()
                true
            } else false
        }

        searchInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        doSearch()
                        true
                    }
                    else -> false
                }
            } else false
        }

        observeData()
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.searchResults.collect { results ->
                        updateResults(results)
                        emptyText.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.isSearching.collect { searching ->
                        searchButton.isEnabled = !searching
                        searchButton.text = if (searching) "搜索中…" else "搜索"
                    }
                }
                launch {
                    viewModel.error.collect { error ->
                        if (error != null) {
                            emptyText.text = error
                            emptyText.visibility = View.VISIBLE
                        } else {
                            emptyText.text = "输入关键词开始搜索"
                        }
                    }
                }
            }
        }
    }

    private fun updateResults(results: List<VideoItem>) {
        rowsAdapter.clear()
        if (results.isNotEmpty()) {
            val header = HeaderItem(0, "搜索结果")
            val cardPresenter = CardPresenter { item ->
                if (item is VideoItem) {
                    Router.navigateToDetail(requireActivity(), item.vodId, item.title, item.coverUrl)
                }
            }
            val adapter = ArrayObjectAdapter(cardPresenter)
            for (item in results) {
                adapter.add(item)
            }
            rowsAdapter.add(ListRow(header, adapter))
        }
    }
}
