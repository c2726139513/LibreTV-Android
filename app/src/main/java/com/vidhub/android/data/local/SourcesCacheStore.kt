package com.vidhub.android.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.vidhub.android.model.ApiSource
import com.vidhub.android.util.Constants
import kotlinx.coroutines.flow.first

private val Context.sourcesCacheDataStore by preferencesDataStore(name = Constants.DATASTORE_SOURCES_CACHE)

/**
 * 各服务器内置数据源列表的本地缓存。
 * 键为服务器 ID，值为 /api/sources 返回的源列表 JSON。
 * 作用：搜索时无需先拉取源列表；服务端源列表临时不可用时仍可搜索。
 */
class SourcesCacheStore(
    private val context: Context,
    moshi: Moshi,
) {

    private val listAdapter = moshi.adapter<List<ApiSource>>(
        Types.newParameterizedType(List::class.java, ApiSource::class.java)
    )

    suspend fun get(serverId: String): List<ApiSource> {
        val prefs = context.sourcesCacheDataStore.data.first()
        val json = prefs[key(serverId)] ?: return emptyList()
        return runCatching { listAdapter.fromJson(json) }.getOrNull() ?: emptyList()
    }

    suspend fun put(serverId: String, sources: List<ApiSource>) {
        context.sourcesCacheDataStore.edit { prefs ->
            prefs[key(serverId)] = listAdapter.toJson(sources)
        }
    }

    suspend fun remove(serverId: String) {
        context.sourcesCacheDataStore.edit { prefs ->
            prefs.remove(key(serverId))
        }
    }

    private fun key(serverId: String) = stringPreferencesKey("server_$serverId")
}
