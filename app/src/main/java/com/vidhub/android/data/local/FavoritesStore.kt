package com.vidhub.android.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.vidhub.android.model.FavoriteItem
import com.vidhub.android.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.favoritesDataStore by preferencesDataStore(name = Constants.DATASTORE_FAVORITES)

/**
 * 收藏持久化（DataStore + JSON 列表）。
 */
class FavoritesStore(
    private val context: Context,
    moshi: Moshi,
) {

    private val listAdapter = moshi.adapter<List<FavoriteItem>>(
        Types.newParameterizedType(List::class.java, FavoriteItem::class.java)
    )

    /** 按收藏时间倒序 */
    val favorites: Flow<List<FavoriteItem>> = context.favoritesDataStore.data.map { prefs ->
        load(prefs[KEY_ITEMS]).sortedByDescending { it.addedAt }
    }

    suspend fun isFavorite(key: String): Boolean =
        favorites.first().any { it.key == key }

    /** 切换收藏状态，返回切换后是否已收藏 */
    suspend fun toggle(item: FavoriteItem): Boolean {
        var added = false
        context.favoritesDataStore.edit { prefs ->
            val current = load(prefs[KEY_ITEMS])
            if (current.any { it.key == item.key }) {
                prefs[KEY_ITEMS] = listAdapter.toJson(current.filterNot { it.key == item.key })
                added = false
            } else {
                val next = (current + item.copy(addedAt = System.currentTimeMillis()))
                    .sortedByDescending { it.addedAt }
                    .take(Constants.FAVORITES_MAX_ITEMS)
                prefs[KEY_ITEMS] = listAdapter.toJson(next)
                added = true
            }
        }
        return added
    }

    suspend fun remove(key: String) {
        context.favoritesDataStore.edit { prefs ->
            prefs[KEY_ITEMS] = listAdapter.toJson(load(prefs[KEY_ITEMS]).filterNot { it.key == key })
        }
    }

    suspend fun clear() {
        context.favoritesDataStore.edit { it.remove(KEY_ITEMS) }
    }

    private fun load(json: String?): List<FavoriteItem> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { listAdapter.fromJson(json) }.getOrNull() ?: emptyList()
    }

    companion object {
        private val KEY_ITEMS = stringPreferencesKey("items")
    }
}
