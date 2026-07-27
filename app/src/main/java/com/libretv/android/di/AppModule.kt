package com.libretv.android.di

import android.content.Context
import com.libretv.android.data.local.ServerConfigStore
import com.libretv.android.data.local.WatchHistoryStore
import com.libretv.android.data.remote.VidHubApi
import com.libretv.android.data.repository.VideoRepository
import com.libretv.android.model.ServerConfig
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
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
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
    fun provideVidHubApi(retrofit: Retrofit): VidHubApi {
        return retrofit.create(VidHubApi::class.java)
    }
}
