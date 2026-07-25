package com.libretv.android.player

import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import com.libretv.android.model.ServerConfig

object ProxyMediaSource {

    fun create(
        mediaItem: MediaItem,
        serverConfigProvider: () -> ServerConfig?,
        userAgent: String = "LibreTV-Android/1.0"
    ): MediaSource {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)

        val proxyDataSourceFactory = ProxyDataSourceFactory(
            httpDataSourceFactory,
            serverConfigProvider
        )

        return HlsMediaSource.Factory(proxyDataSourceFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(mediaItem)
    }

    fun createDataSourceFactory(
        serverConfigProvider: () -> ServerConfig?
    ): ProxyDataSourceFactory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("LibreTV-Android/1.0")
            .setAllowCrossProtocolRedirects(true)
        return ProxyDataSourceFactory(httpDataSourceFactory, serverConfigProvider)
    }
}
