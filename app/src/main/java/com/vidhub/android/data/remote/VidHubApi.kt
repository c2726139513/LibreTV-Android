package com.vidhub.android.data.remote

import com.vidhub.android.data.remote.dto.DetailResponse
import com.vidhub.android.data.remote.dto.PasswordResponse
import com.vidhub.android.data.remote.dto.SearchResponse
import com.vidhub.android.data.remote.dto.SourcesResponse
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * VidHub 服务端 API。
 *
 * 所有接口都通过完整 URL 调用（@Url），URL 由
 * [com.vidhub.android.data.repository.VideoRepository] 的 buildVidHubUrl() 统一构造，
 * 携带 auth=sha256(password) 与 t=timestamp 查询参数。
 */
interface VidHubApi {

    /** 搜索：{server}/api/search?wd=&apiUrl=&auth=&t=&pg= */
    @GET
    suspend fun search(@Url url: String): SearchResponse

    /** 详情：{server}/api/detail?id=&apiUrl=&auth=&t=（自定义源可带 customDetail=） */
    @GET
    suspend fun detail(@Url url: String): DetailResponse

    /** 服务器内置数据源列表：{server}/api/sources?auth=&t= */
    @GET
    suspend fun getSources(@Url url: String): SourcesResponse

    /** 服务器密码哈希：{server}/api/env/password（无需鉴权） */
    @GET
    suspend fun getPasswordHash(@Url url: String): PasswordResponse
}
