package com.vidhub.android.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.vidhub.android.model.WatchHistoryItem
import com.vidhub.android.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.historyDataStore by preferencesDataStore(name = Constants.DATASTORE_HISTORY)

/**
 * 播放历史持久化（DataStore + JSON 列表）。
 * 按更新时间倒序，超出上限时淘汰最旧记录。
 */
class WatchHistoryStore(
    private val context: Context,
    moshi: Moshi,
) {

    private val listAdapter = moshi.adapter<List<WatchHistoryItem>>(
        Types.newParameterizedType(List::class.java, WatchHistoryItem::class.java)
    )

    /** 按最后观看时间倒序的历史列表 */
    val history: Flow<List<WatchHistoryItem>> = context.historyDataStore.data.map { prefs ->
        load(prefs[KEY_ITEMS]).sortedByDescending { it.updatedAt }
    }

    suspend fun get(key: String): WatchHistoryItem? =
        history.first().firstOrNull { it.key == key }

    /** 写入/更新一条播放进度，并置顶到最前 */
    suspend fun saveProgress(item: WatchHistoryItem) {
        context.historyDataStore.edit { prefs ->
            val current = load(prefs[KEY_ITEMS])
                .filterNot { it.key == item.key }
                .toMutableList()
            current.add(item.copy(updatedAt = System.currentTimeMillis()))
            val trimmed = current
                .sortedByDescending { it.updatedAt }
                .take(Constants.HISTORY_MAX_ITEMS)
            prefs[KEY_ITEMS] = listAdapter.toJson(trimmed)
        }
    }

    suspend fun remove(key: String) {
        context.historyDataStore.edit { prefs ->
            val current = load(prefs[KEY_ITEMS]).filterNot { it.key == key }
            prefs[KEY_ITEMS] = listAdapter.toJson(current)
        }
    }

    suspend fun clear() {
        context.historyDataStore.edit { it.remove(KEY_ITEMS) }
    }

    private fun load(json: String?): List<WatchHistoryItem> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { listAdapter.fromJson(json) }.getOrNull() ?: emptyList()
    }

    companion object {
        private val KEY_ITEMS = stringPreferencesKey("items")
    }
}
