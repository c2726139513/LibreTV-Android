package com.libretv.android.ui.detail

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DetailsOverviewRow
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.libretv.android.model.Episode
import com.libretv.android.model.VideoItem
import com.libretv.android.navigation.Router
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DetailFragment : DetailsSupportFragment() {

    private val viewModel: DetailViewModel by viewModels()
    private var videoId: String = ""

    companion object {
        private const val ARG_VIDEO_ID = "video_id"
        private const val ARG_VIDEO_TITLE = "video_title"
        private const val ARG_COVER_URL = "cover_url"

        fun newInstance(videoId: String, title: String, coverUrl: String?): DetailFragment {
            return DetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_VIDEO_ID, videoId)
                    putString(ARG_VIDEO_TITLE, title)
                    putString(ARG_COVER_URL, coverUrl)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            videoId = it.getString(ARG_VIDEO_ID, "")
        }

        if (videoId.isNotBlank()) {
            viewModel.loadDetail(videoId)
        }

        observeData()
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.video.collect { video ->
                        video?.let { updateUI(it) }
                    }
                }
            }
        }
    }

    private fun updateUI(video: VideoItem) {
        val presenterSelector = ClassPresenterSelector().apply {
            addClassPresenter(
                DetailsOverviewRow::class.java,
                FullWidthDetailsOverviewRowPresenter(DetailsDescriptionPresenter())
            )
            addClassPresenter(ListRow::class.java, ListRowPresenter())
        }
        val adapter = ArrayObjectAdapter(presenterSelector)

        // Detail row
        val detailsRow = DetailsOverviewRow(video)
        adapter.add(detailsRow)

        // Episodes row
        if (video.episodes.isNotEmpty()) {
            val episodeHeader = HeaderItem("episodes", "剧集列表")
            val episodePresenter = CardPresenter()
            val episodeAdapter = ArrayObjectAdapter(episodePresenter)
            for (episode in video.episodes) {
                episodeAdapter.add(episode)
            }
            adapter.add(ListRow(episodeHeader, episodeAdapter))
        }

        setAdapter(adapter)

        // Handle item clicks
        setOnItemViewClickedListener { itemViewHolder, item, rowViewHolder, row ->
            when (item) {
                is Episode -> {
                    viewModel.selectEpisode(item.index)
                    val activeVideo = viewModel.video.value
                    if (activeVideo != null) {
                        Router.navigateToPlayback(
                            requireActivity(),
                            item.url,
                            activeVideo.title,
                            item.name,
                            item.index
                        )
                    }
                }
            }
        }
    }
}
