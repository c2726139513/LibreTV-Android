package com.vidhub.android.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.vidhub.android.model.ApiSource
import com.vidhub.android.model.ServerConfig
import com.vidhub.android.util.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

/**
 * 服务器配置持久化。
 *
 * 使用 EncryptedSharedPreferences 加密存储（含明文密码），
 * 少数 Keystore 不可用的设备（如部分 API 21-22 电视盒）自动降级为普通 SharedPreferences。
 *
 * 存储内容：
 *  - 服务器列表（URL + 密码）
 *  - 当前选中的服务器 ID
 *  - 每个服务器的自定义 CMS 源列表
 */
class ServerConfigStore(
    context: Context,
    moshi: Moshi,
) {

    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

    private val serversAdapter = moshi.adapter<List<ServerConfig>>(
        Types.newParameterizedType(List::class.java, ServerConfig::class.java)
    )
    private val sourcesAdapter = moshi.adapter<List<ApiSource>>(
        Types.newParameterizedType(List::class.java, ApiSource::class.java)
    )

    // ---------- 服务器列表 ----------

    val servers: Flow<List<ServerConfig>> = callbackFlow {
        trySend(loadServers())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SERVERS) trySend(loadServers())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val activeServerId: Flow<String?> = callbackFlow {
        trySend(prefs.getString(KEY_ACTIVE_ID, null))
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_ACTIVE_ID) trySend(prefs.getString(KEY_ACTIVE_ID, null))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun getServersSnapshot(): List<ServerConfig> = loadServers()

    fun getActiveServerSnapshot(): ServerConfig? {
        val id = prefs.getString(KEY_ACTIVE_ID, null) ?: return null
        return loadServers().firstOrNull { it.id == id }
    }

    fun getServer(id: String): ServerConfig? = loadServers().firstOrNull { it.id == id }

    /** 新增服务器；若是第一台则自动设为选中。返回带生成 ID 的配置。 */
    @Synchronized
    fun addServer(name: String, url: String, password: String): ServerConfig {
        val config = ServerConfig(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            url = normalizeUrl(url),
            password = password,
        )
        val list = loadServers() + config
        saveServers(list)
        if (prefs.getString(KEY_ACTIVE_ID, null) == null) {
            prefs.edit().putString(KEY_ACTIVE_ID, config.id).apply()
        }
        return config
    }

    @Synchronized
    fun updateServer(config: ServerConfig) {
        val list = loadServers().map { if (it.id == config.id) config.copy(url = normalizeUrl(config.url)) else it }
        saveServers(list)
    }

    @Synchronized
    fun removeServer(id: String) {
        saveServers(loadServers().filterNot { it.id == id })
        prefs.edit()
            .remove(KEY_SOURCES_PREFIX + id)
            .apply()
        if (prefs.getString(KEY_ACTIVE_ID, null) == id) {
            val fallback = loadServers().firstOrNull()?.id
            prefs.edit().putString(KEY_ACTIVE_ID, fallback).apply()
        }
    }

    fun setActiveServer(id: String) {
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    // ---------- 自定义源 ----------

    fun getCustomSources(serverId: String): List<ApiSource> {
        val json = prefs.getString(KEY_SOURCES_PREFIX + serverId, null) ?: return emptyList()
        return runCatching { sourcesAdapter.fromJson(json) }.getOrNull() ?: emptyList()
    }

    @Synchronized
    fun setCustomSources(serverId: String, sources: List<ApiSource>) {
        prefs.edit()
            .putString(KEY_SOURCES_PREFIX + serverId, sourcesAdapter.toJson(sources))
            .apply()
    }

    @Synchronized
    fun addCustomSource(serverId: String, source: ApiSource) {
        val current = getCustomSources(serverId)
        setCustomSources(serverId, current + source)
    }

    @Synchronized
    fun removeCustomSource(serverId: String, sourceKey: String) {
        setCustomSources(serverId, getCustomSources(serverId).filterNot { it.key == sourceKey })
    }

    // ---------- 内部实现 ----------

    private fun loadServers(): List<ServerConfig> {
        val json = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return runCatching { serversAdapter.fromJson(json) }.getOrNull() ?: emptyList()
    }

    private fun saveServers(list: List<ServerConfig>) {
        prefs.edit().putString(KEY_SERVERS, serversAdapter.toJson(list)).apply()
    }

    private fun normalizeUrl(url: String): String {
        var result = url.trim().trimEnd('/')
        if (!result.startsWith("http://") && !result.startsWith("https://")) {
            result = "https://$result"
        }
        return result
    }

    private fun createPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                Constants.PREFS_SECURE_SERVERS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            context.getSharedPreferences(Constants.PREFS_SECURE_SERVERS, Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val KEY_SERVERS = "servers"
        private const val KEY_ACTIVE_ID = "active_server_id"
        private const val KEY_SOURCES_PREFIX = "custom_sources_"
    }
}
