package com.vidhub.android.navigation

import android.content.Context
import android.content.Intent
import com.vidhub.android.model.VideoItem
import com.vidhub.android.ui.detail.DetailsActivity
import com.vidhub.android.ui.player.PlaybackActivity
import com.vidhub.android.ui.search.SearchActivity
import com.vidhub.android.ui.settings.ServerEditActivity
import com.vidhub.android.ui.settings.SettingsActivity
import com.vidhub.android.util.Constants

/**
 * 页面路由：集中管理页面跳转 Intent 的构造。
 */
object Router {

    fun openSearch(context: Context) {
        context.startActivity(Intent(context, SearchActivity::class.java))
    }

    fun openDetail(context: Context, item: VideoItem) {
        context.startActivity(
            Intent(context, DetailsActivity::class.java)
                .putExtra(Constants.EXTRA_VIDEO_ITEM, item)
        )
    }

    /**
     * 打开播放器。
     *
     * @param episodeUrls    详情接口返回的剧集 URL 列表（直连播放，无代理）
     * @param episodeIndex   起始集下标
     * @param startPositionMs 续播位置（0 表示从头播放）
     */
    fun openPlayer(
        context: Context,
        item: VideoItem,
        episodeUrls: List<String>,
        episodeIndex: Int,
        startPositionMs: Long = 0L,
    ) {
        context.startActivity(
            Intent(context, PlaybackActivity::class.java)
                .putExtra(Constants.EXTRA_VIDEO_ITEM, item)
                .putStringArrayListExtra(Constants.EXTRA_EPISODE_URLS, ArrayList(episodeUrls))
                .putExtra(Constants.EXTRA_EPISODE_INDEX, episodeIndex)
                .putExtra(Constants.EXTRA_START_POSITION_MS, startPositionMs)
        )
    }

    fun openSettings(context: Context) {
        context.startActivity(Intent(context, SettingsActivity::class.java))
    }

    /** 打开服务器编辑页；serverId 为 null 表示新增 */
    fun openServerEdit(context: Context, serverId: String?) {
        context.startActivity(
            Intent(context, ServerEditActivity::class.java)
                .putExtra(Constants.EXTRA_SERVER_ID, serverId)
        )
    }
}
