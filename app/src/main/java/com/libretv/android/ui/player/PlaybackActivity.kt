package com.libretv.android.ui.player

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.libretv.android.R
import com.libretv.android.model.ServerConfig
import com.libretv.android.player.ProxyMediaSource
import com.libretv.android.util.Sha256
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackActivity : ComponentActivity() {

    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    private var videoUrl: String = ""
    private var videoTitle: String = ""
    private var episodeIndex: Int = 0
    private var episodeName: String? = null
    private var videoId: String = ""

    @Inject
    lateinit var serverConfigProvider: () -> ServerConfig?

    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.player_view)

        intent?.let {
            videoUrl = it.getStringExtra("video_url") ?: ""
            videoTitle = it.getStringExtra("video_title") ?: ""
            episodeName = it.getStringExtra("episode_name")
            episodeIndex = it.getIntExtra("episode_index", 0)
        }

        initializePlayer()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            val mediaItem = MediaItem.Builder()
                .setUri(videoUrl)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(videoTitle)
                        .setSubtitle(episodeName)
                        .build()
                )
                .build()

            val mediaSource = ProxyMediaSource.create(
                mediaItem = mediaItem,
                serverConfigProvider = serverConfigProvider
            )

            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    // Playback state tracking handled externally
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    // Track playback state changes
                }
            })
        }

        playerView.player = player
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                saveProgress()
                finish()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                playerView.useController = !playerView.useController
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onPause() {
        super.onPause()
        saveProgress()
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    private fun saveProgress() {
        player?.let { p ->
            viewModel.savePlaybackProgress(
                videoId = videoUrl,
                title = videoTitle,
                coverUrl = null,
                episodeIndex = episodeIndex,
                episodeName = episodeName,
                position = p.currentPosition,
                duration = p.duration
            )
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }
}
