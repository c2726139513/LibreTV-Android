package com.vidhub.android.ui.browse

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vidhub.android.R
import com.vidhub.android.data.local.WatchHistoryItem
import com.vidhub.android.model.ServerConfig
import com.vidhub.android.model.SettingsAction
import com.vidhub.android.model.VideoItem
import com.vidhub.android.navigation.Router
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BrowseFragment : BrowseSupportFragment() {

    private val viewModel: BrowseViewModel by viewModels()
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        observeData()
    }

    private fun setupUI() {
        title = getString(R.string.app_name)
        // Badge would be set here in production:
        // setBadgeDrawable(...)

        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true

        // Search affordance click -> navigate to search
        setOnSearchClickedListener {
            Router.navigateToSearch(requireActivity())
        }

        // Add persistent settings row (always at the end)
        ensureSettingsAtEnd()

        adapter = rowsAdapter

        setOnItemViewClickedListener { _: Presenter.ViewHolder, item: Any, _: RowPresenter.ViewHolder, _: Row ->
            when (item) {
                is VideoItem -> {
                    Router.navigateToDetail(
                        requireActivity(),
                        item.vodId,
                        item.title,
                        item.coverUrl
                    )
                }
                is WatchHistoryItem -> {
                    val activeServer = viewModel.activeServer.value
                    if (activeServer != null) {
                        Router.navigateToPlayback(
                            requireActivity(),
                            "", // videoUrl will be loaded from detail
                            item.title,
                            item.episodeName,
                            item.episodeIndex
                        )
                    }
                }
                is SettingsAction -> {
                    Router.navigateToSettings(requireActivity())
                }
            }
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.continueWatching.collect { history ->
                        updateContinueWatchingRow(history)
                    }
                }
                launch {
                    viewModel.servers.collect { servers ->
                        updateServerRows(servers)
                    }
                }
            }
        }
    }

    private fun updateContinueWatchingRow(history: List<WatchHistoryItem>) {
        removeRowWithHeader("继续观看")

        if (history.isNotEmpty()) {
            val header = HeaderItem(getString(R.string.continue_watching))
            val cardPresenter = CardPresenter()
            val adapter = ArrayObjectAdapter(cardPresenter)
            for (item in history) {
                adapter.add(item)
            }
            rowsAdapter.add(0, ListRow(header, adapter))
        }
        ensureSettingsAtEnd()
    }

    private fun updateServerRows(servers: List<ServerConfig>) {
        removeRowsWithPrefix("server_")

        servers.forEachIndexed { index, server ->
            val header = HeaderItem(server.name)
            val cardPresenter = CardPresenter()
            val adapter = ArrayObjectAdapter(cardPresenter)
            // Placeholder: server content will be loaded in a future iteration
            rowsAdapter.add(ListRow(header, adapter))
        }
        ensureSettingsAtEnd()
    }

    private fun ensureSettingsAtEnd() {
        removeRowWithHeader("设置")
        val header = HeaderItem("设置")
        val presenter = CardPresenter()
        val adapter = ArrayObjectAdapter(presenter)
        adapter.add(SettingsAction)
        rowsAdapter.add(ListRow(header, adapter))
    }

    private fun removeRowWithHeader(name: String) {
        for (i in (0 until rowsAdapter.size()).reversed()) {
            val row = rowsAdapter.get(i) as? ListRow ?: continue
            if (row.headerItem?.name == name) {
                rowsAdapter.remove(row)
                break
            }
        }
    }

    private fun removeRowsWithPrefix(prefix: String) {
        val toRemove = mutableListOf<Any>()
        for (i in 0 until rowsAdapter.size()) {
            val row = rowsAdapter.get(i) as? ListRow ?: continue
            if (row.headerItem?.name?.startsWith(prefix) == true) {
                toRemove.add(row)
            }
        }
        toRemove.forEach { rowsAdapter.remove(it) }
    }
}
