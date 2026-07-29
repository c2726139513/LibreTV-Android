package com.vidhub.android.ui.browse

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vidhub.android.R
import com.vidhub.android.model.FavoriteItem
import com.vidhub.android.model.ServerConfig
import com.vidhub.android.model.VideoItem
import com.vidhub.android.model.WatchHistoryItem
import com.vidhub.android.navigation.Router
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 首页：服务器行 + 继续观看 + 我的收藏。
 * 左上角搜索球进入搜索页。
 */
@AndroidEntryPoint
class BrowseFragment : BrowseSupportFragment() {

    private val viewModel: BrowseViewModel by viewModels()

    private lateinit var rowsAdapter: ArrayObjectAdapter

    /** stableKey → 历史记录，用于点击卡片时取回播放进度 */
    private var historyIndex: Map<String, WatchHistoryItem> = emptyMap()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = ContextCompat.getColor(requireContext(), R.color.surface)
        searchAffordanceColor = ContextCompat.getColor(requireContext(), R.color.brand_primary)

        setOnSearchClickedListener { Router.openSearch(requireContext()) }

        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        adapter = rowsAdapter

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        onItemViewClickedListener =
            androidx.leanback.widget.OnItemViewClickedListener { _, item, _, row ->
                val rowId = (row as? ListRow)?.headerItem?.id
                when (item) {
                    is TextCard -> handleTextCardClick(item)
                    is VideoItem -> when (rowId) {
                        ROW_HISTORY -> {
                            val history = historyIndex[item.stableKey]
                            if (history != null) viewModel.resume(history)
                        }
                        else -> Router.openDetail(requireContext(), item)
                    }
                }
            }
    }

    private fun handleTextCardClick(card: TextCard) {
        when (card.payload) {
            is ServerConfig -> viewModel.setActiveServer(card.payload.id)
            ACTION_ADD_SERVER -> Router.openServerEdit(requireContext(), null)
            ACTION_MANAGE_SERVERS -> Router.openSettings(requireContext())
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                launch {
                    viewModel.resumeEvents.collect { event ->
                        when (event) {
                            is BrowseViewModel.ResumeEvent.Open -> Router.openPlayer(
                                requireContext(), event.item, event.episodeUrls,
                                event.episodeIndex, event.startPositionMs,
                            )
                            is BrowseViewModel.ResumeEvent.Failed ->
                                Toast.makeText(requireContext(), event.reason, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun render(state: BrowseViewModel.BrowseState) {
        historyIndex = state.history.associateBy { it.key }
        rowsAdapter.clear()

        rowsAdapter.add(buildServersRow(state))

        if (state.history.isNotEmpty()) {
            rowsAdapter.add(buildHistoryRow(state.history))
        }
        if (state.favorites.isNotEmpty()) {
            rowsAdapter.add(buildFavoritesRow(state.favorites))
        }
    }

    private fun buildServersRow(state: BrowseViewModel.BrowseState): ListRow {
        val cards = ArrayObjectAdapter(TextCardPresenter())
        state.servers.forEach { server ->
            val isActive = server.id == state.activeServerId
            cards.add(
                TextCard(
                    id = "server_${server.id}",
                    title = (if (isActive) "✓ " else "") + server.name,
                    subtitle = if (isActive) "${server.url}\n${getString(R.string.settings_active)}" else server.url,
                    payload = server,
                )
            )
        }
        cards.add(
            TextCard(
                id = "add_server",
                title = "+ ${getString(R.string.add_server)}",
                subtitle = getString(R.string.server_field_url_hint),
                payload = ACTION_ADD_SERVER,
            )
        )
        if (state.servers.isNotEmpty()) {
            cards.add(
                TextCard(
                    id = "manage_servers",
                    title = getString(R.string.manage_servers),
                    subtitle = "切换 / 编辑 / 删除 / 数据源",
                    payload = ACTION_MANAGE_SERVERS,
                )
            )
        }
        return ListRow(HeaderItem(ROW_SERVERS, getString(R.string.row_servers)), cards)
    }

    private fun buildHistoryRow(history: List<WatchHistoryItem>): ListRow {
        val cards = ArrayObjectAdapter(VideoCardPresenter())
        history.forEach { h ->
            val progressText = if (h.durationMs > 0) {
                val percent = (h.positionMs * 100 / h.durationMs).toInt()
                "第${h.episodeIndex + 1}集 · $percent%"
            } else {
                "第${h.episodeIndex + 1}集"
            }
            cards.add(
                VideoItem(
                    vodId = h.vodId,
                    title = h.title,
                    coverUrl = h.coverUrl,
                    remarks = progressText,
                    sourceApi = h.sourceApi,
                    serverId = h.serverId,
                )
            )
        }
        return ListRow(HeaderItem(ROW_HISTORY, getString(R.string.row_continue_watching)), cards)
    }

    private fun buildFavoritesRow(favorites: List<FavoriteItem>): ListRow {
        val cards = ArrayObjectAdapter(VideoCardPresenter())
        favorites.forEach { f ->
            cards.add(
                VideoItem(
                    vodId = f.vodId,
                    title = f.title,
                    coverUrl = f.coverUrl,
                    remarks = f.remarks,
                    sourceApi = f.sourceApi,
                    serverId = f.serverId,
                )
            )
        }
        return ListRow(HeaderItem(ROW_FAVORITES, getString(R.string.row_favorites)), cards)
    }

    companion object {
        private const val ROW_SERVERS = 0L
        private const val ROW_HISTORY = 1L
        private const val ROW_FAVORITES = 2L

        private val ACTION_ADD_SERVER = Any()
        private val ACTION_MANAGE_SERVERS = Any()
    }
}
