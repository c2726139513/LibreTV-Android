package com.libretv.android.player

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.libretv.android.model.ServerConfig
import com.libretv.android.util.Sha256
import java.io.IOException

class ProxyDataSourceFactory(
    private val baseDataSourceFactory: DataSource.Factory,
    private val serverConfigProvider: () -> ServerConfig?
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return ProxyDataSource(
            baseDataSourceFactory.createDataSource(),
            serverConfigProvider
        )
    }
}

class ProxyDataSource(
    private val base: DataSource,
    private val serverConfigProvider: () -> ServerConfig?
) : DataSource {

    override fun open(dataSpec: DataSpec): Long {
        val config = serverConfigProvider()
        if (config == null) {
            return base.open(dataSpec)
        }

        val originalUri = dataSpec.uri
        val originalStr = originalUri.toString()

        // If already a proxy URL, just add auth params if missing
        val newSpec = if (originalStr.contains("/proxy/")) {
            val uriStr = ensureAuthParams(originalStr, config)
            dataSpec.withUri(Uri.parse(uriStr))
        } else {
            // Wrap in proxy URL
            val proxyUrl = buildProxyUrl(config, originalStr)
            dataSpec.withUri(Uri.parse(proxyUrl))
        }
        return base.open(newSpec)
    }

    private fun buildProxyUrl(config: ServerConfig, originalUrl: String): String {
        val timestamp = System.currentTimeMillis()
        val hash = Sha256.hash(config.password)
        val encoded = Uri.encode(originalUrl)
        val baseUrl = config.url.trimEnd('/')
        return "$baseUrl/proxy/$encoded?auth=$hash&t=$timestamp"
    }

    private fun ensureAuthParams(url: String, config: ServerConfig): String {
        val uri = Uri.parse(url)
        if (uri.getQueryParameter("auth") != null) {
            return url // already has auth
        }
        val timestamp = System.currentTimeMillis()
        val hash = Sha256.hash(config.password)
        return url + (if (url.contains("?")) "&" else "?") + "auth=$hash&t=$timestamp"
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return base.read(buffer, offset, length)
    }

    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        base.addTransferListener(transferListener)
    }

    override fun close() {
        base.close()
    }

    override fun getResponseHeaders(): Map<String, List<String>> = base.getResponseHeaders()

    override fun getUri(): Uri? = base.getUri()
}
