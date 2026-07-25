package com.libretv.android.data.remote

import com.libretv.android.model.ServerConfig
import com.libretv.android.util.Sha256
import okhttp3.Interceptor
import okhttp3.Response

class ProxyInterceptor(
    private val serverConfigProvider: () -> ServerConfig?
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url

        if (url.encodedPath.contains("/proxy/")) {
            val config = serverConfigProvider()
            if (config != null) {
                val timestamp = System.currentTimeMillis()
                val authHash = Sha256.hash(config.password)
                val newUrl = url.newBuilder()
                    .addQueryParameter("auth", authHash)
                    .addQueryParameter("t", timestamp.toString())
                    .build()
                val newRequest = original.newBuilder().url(newUrl).build()
                return chain.proceed(newRequest)
            }
        }

        // For non-proxy requests or when no config, just set UA
        val requestWithUA = original.newBuilder()
            .header("User-Agent", "LibreTV-Android/1.0")
            .build()
        return chain.proceed(requestWithUA)
    }
}
