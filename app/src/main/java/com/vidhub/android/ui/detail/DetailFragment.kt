package com.vidhub.android.ui.detail

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.fragment.app.viewModels
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DetailsOverviewRow
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.imageLoader
import coil.request.ImageRequest
import com.vidhub.android.R
import com.vidhub.android.model.Episode
import com.vidhub.android.model.VideoItem
import com.vidhub.android.navigation.Router
import com.vidhub.android.ui.browse.TextCard
import com.vidhub.android.ui.browse.TextCardPresenter
import com.vidhub.android.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 视频详情页：简介 + 播放/收藏操作 + 剧集列表。
 */
@AndroidEntryPoint
class DetailFragment : DetailsSupportFragment() {

    private val viewModel: DetailViewModel by viewModels()

    private lateinit var rowsAdapter: ArrayObjectAdapter
    private var errorShown = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val selector = ClassPresenterSelector().apply {
            addClassPresenter(
                DetailsOverviewRow::class.java,
                FullWidthDetailsOverviewRowPresenter(DetailsDescriptionPresenter()),
            )
            addClassPresenter(ListRow::class.java, ListRowPresenter())
        }
        rowsAdapter = ArrayObjectAdapter(selector)
        adapter = rowsAdapter

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is Action -> when (item.id) {
                    ACTION_PLAY -> playFromHistory()
                    ACTION_FAVORITE -> viewModel.toggleFavorite()
                }
                is TextCard -> (item.payload as? Episode)?.let { playAt(it.index) }
            }
        }

        val item = IntentCompat.getParcelableExtra(
            requireActivity().intent, Constants.EXTRA_VIDEO_ITEM, VideoItem::class.java,
        )
        if (item == null) {
            requireActivity().finish()
            return
        }
        viewModel.load(item)
        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    private fun render(state: DetailViewModel.DetailUiState) {
        val item = state.item ?: return

        rowsAdapter.clear()

        // 概览行：封面 + 标题/简介 + 操作按钮
        val overviewRow = DetailsOverviewRow(item)
        val actions = ArrayObjectAdapter()
        if (state.episodes.isNotEmpty() || state.loading) {
            actions.add(Action(ACTION_PLAY, state.playLabel))
        }
        actions.add(
            Action(
                ACTION_FAVORITE,
                getString(if (state.isFavorite) R.string.action_favorite_remove else R.string.action_favorite_add),
            )
        )
        overviewRow.actionsAdapter = actions
        rowsAdapter.add(overviewRow)
        loadCover(overviewRow, item.coverUrl)

        // 剧集行
        if (state.episodes.isNotEmpty()) {
            val episodesAdapter = ArrayObjectAdapter(TextCardPresenter())
            state.episodes.forEach { episode ->
                episodesAdapter.add(TextCard(id = "ep_${episode.index}", title = episode.name, payload = episode))
            }
            rowsAdapter.add(ListRow(HeaderItem(getString(R.string.row_episodes)), episodesAdapter))
        }

        // 错误提示（只弹一次）
        if (!state.loading && state.error != null && state.episodes.isEmpty() && !errorShown) {
            errorShown = true
            Toast.makeText(requireContext(), state.error, Toast.LENGTH_LONG).show()
        }
    }

    private fun loadCover(row: DetailsOverviewRow, url: String?) {
        val context = requireContext()
        val placeholder = ContextCompat.getDrawable(context, R.drawable.poster_placeholder)
        if (url.isNullOrBlank()) {
            row.imageDrawable = placeholder
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val drawable = context.imageLoader.execute(
                ImageRequest.Builder(context).data(url).build()
            ).drawable
            row.imageDrawable = drawable ?: placeholder
        }
    }

    /** 主播放按钮：从历史进度续播 */
    private fun playFromHistory() {
        val state = viewModel.uiState.value
        val item = state.item ?: return
        if (state.episodes.isEmpty()) return
        val history = state.history
        val index = history?.episodeIndex?.coerceIn(0, state.episodes.size - 1) ?: 0
        val position = history?.positionMs ?: 0L
        Router.openPlayer(requireContext(), item, state.episodes.map { it.url }, index, position)
    }

    /** 点击某集：从头播放该集 */
    private fun playAt(index: Int) {
        val state = viewModel.uiState.value
        val item = state.item ?: return
        Router.openPlayer(requireContext(), item, state.episodes.map { it.url }, index, 0L)
    }

    companion object {
        private const val ACTION_PLAY = 1L
        private const val ACTION_FAVORITE = 2L
    }
}
