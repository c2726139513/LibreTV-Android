package com.libretv.android.navigation

import android.content.Intent
import androidx.fragment.app.FragmentActivity
import com.libretv.android.R
import com.libretv.android.ui.detail.DetailFragment
import com.libretv.android.ui.player.PlaybackActivity
import com.libretv.android.ui.settings.SettingsFragment

object Router {

    fun navigateToDetail(
        activity: FragmentActivity,
        videoId: String,
        videoTitle: String,
        coverUrl: String?
    ) {
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_frame, DetailFragment.newInstance(videoId, videoTitle, coverUrl))
            .addToBackStack("detail")
            .commit()
    }

    fun navigateToSettings(activity: FragmentActivity) {
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_frame, SettingsFragment())
            .addToBackStack("settings")
            .commit()
    }

    fun navigateToPlayback(
        activity: FragmentActivity,
        videoUrl: String,
        videoTitle: String,
        episodeName: String? = null,
        episodeIndex: Int = 0
    ) {
        val intent = Intent(activity, PlaybackActivity::class.java).apply {
            putExtra("video_url", videoUrl)
            putExtra("video_title", videoTitle)
            putExtra("episode_name", episodeName)
            putExtra("episode_index", episodeIndex)
        }
        activity.startActivity(intent)
    }

    fun navigateToSearch(activity: FragmentActivity) {
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_frame, com.libretv.android.ui.search.SearchFragment())
            .addToBackStack("search")
            .commit()
    }

    fun navigateBack(activity: FragmentActivity) {
        activity.supportFragmentManager.popBackStack()
    }
}
