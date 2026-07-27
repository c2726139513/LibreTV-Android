package com.vidhub.android.data.remote

import com.vidhub.android.data.remote.dto.DetailResponse
import com.vidhub.android.data.remote.dto.PasswordResponse
import com.vidhub.android.data.remote.dto.SearchResponse
import retrofit2.http.GET
import retrofit2.http.Url

interface VidHubApi {
    @GET
    suspend fun search(@Url url: String): SearchResponse

    @GET
    suspend fun detail(@Url url: String): DetailResponse

    @GET
    suspend fun getPasswordHash(@Url url: String): PasswordResponse
}
