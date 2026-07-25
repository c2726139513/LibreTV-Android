package com.libretv.android.ui.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
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
import com.libretv.android.R
import com.libretv.android.model.VideoItem
import com.libretv.android.navigation.Router
import com.libretv.android.ui.browse.CardPresenter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : Fragment(R.layout.search_fragment) {

    private val viewModel: SearchViewModel by viewModels()
    private lateinit var searchInput: EditText
    private lateinit var resultsGrid: VerticalGridView
    private lateinit var emptyText: TextView
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchInput = view.findViewById(R.id.search_input)
        resultsGrid = view.findViewById(R.id.search_results)
        emptyText = view.findViewById(R.id.search_empty)

        resultsGrid.adapter = ItemBridgeAdapter(rowsAdapter)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.search(s?.toString() ?: "")
            }
        })

        searchInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                viewModel.search(searchInput.text.toString())
                true
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
