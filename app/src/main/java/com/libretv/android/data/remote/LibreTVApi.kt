package com.libretv.android.data.remote

import com.libretv.android.data.remote.dto.SearchResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

interface LibreTVApi {
    @GET
    suspend fun search(@Url url: String): SearchResponse

    @GET
    suspend fun detail(@Url url: String): SearchResponse

    @Streaming
    @GET
    suspend fun proxyVideo(@Url url: String): ResponseBody
}
