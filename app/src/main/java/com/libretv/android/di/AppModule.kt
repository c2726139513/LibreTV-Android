package com.libretv.android.di

import android.content.Context
import com.libretv.android.data.local.ServerConfigStore
import com.libretv.android.data.local.WatchHistoryStore
import com.libretv.android.data.remote.LibreTVApi
import com.libretv.android.data.remote.ProxyInterceptor
import com.libretv.android.data.repository.VideoRepository
import com.libretv.android.model.ServerConfig
import com.libretv.android.player.ProxyDataSourceFactory
import com.libretv.android.player.ProxyMediaSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideServerConfigStore(@ApplicationContext context: Context): ServerConfigStore {
        return ServerConfigStore(context)
    }

    @Provides
    @Singleton
    fun provideWatchHistoryStore(@ApplicationContext context: Context): WatchHistoryStore {
        return WatchHistoryStore(context)
    }

    @Provides
    @Singleton
    fun provideServerConfigProvider(serverConfigStore: ServerConfigStore): () -> ServerConfig? {
        return { serverConfigStore.getActiveServerSync() }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        proxyInterceptor: ProxyInterceptor
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(proxyInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://placeholder/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideLibreTVApi(retrofit: Retrofit): LibreTVApi {
        return retrofit.create(LibreTVApi::class.java)
    }

    @Provides
    @Singleton
    fun provideProxyInterceptor(
        serverConfigProvider: () -> ServerConfig?
    ): ProxyInterceptor {
        return ProxyInterceptor(serverConfigProvider)
    }
}
