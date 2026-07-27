package com.vidhub.android.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.vidhub.android.model.ServerConfig
import com.vidhub.android.util.Constants
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.lang.reflect.ParameterizedType

class ServerConfigStore(private val context: Context) {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val listType = object : ParameterizedType {
        override fun getRawType() = List::class.java
        override fun getActualTypeArguments() = arrayOf(ServerConfig::class.java)
        override fun getOwnerType() = null
    }
    private val adapter = moshi.adapter<List<ServerConfig>>(listType)

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            Constants.PREFS_NAME + "_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getServers(): Flow<List<ServerConfig>> = flow {
        emit(getServerList())
    }.flowOn(Dispatchers.IO)

    suspend fun addServer(config: ServerConfig) = withContext(Dispatchers.IO) {
        val servers = getServerList().toMutableList()
        servers.add(config)
        saveServerList(servers)
    }

    suspend fun updateServer(config: ServerConfig) = withContext(Dispatchers.IO) {
        val servers = getServerList().toMutableList()
        val index = servers.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            servers[index] = config
            saveServerList(servers)
        }
    }

    suspend fun removeServer(id: String) = withContext(Dispatchers.IO) {
        val servers = getServerList().filter { it.id != id }
        saveServerList(servers)
    }

    suspend fun setActiveServer(id: String) = withContext(Dispatchers.IO) {
        val servers = getServerList().map { it.copy(isActive = it.id == id) }
        saveServerList(servers)
        prefs.edit().putString(Constants.ACTIVE_SERVER_ID_KEY, id).apply()
    }

    fun getServerById(id: String): ServerConfig? = getServerList().find { it.id == id }

    fun getActiveServer(): Flow<ServerConfig?> = flow {
        emit(getActiveServerSync())
    }.flowOn(Dispatchers.IO)

    fun getActiveServerSync(): ServerConfig? {
        val activeId = prefs.getString(Constants.ACTIVE_SERVER_ID_KEY, null) ?: return null
        return getServerList().find { it.id == activeId }
    }

    private fun getServerList(): List<ServerConfig> {
        val json = prefs.getString(Constants.SERVERS_KEY, null) ?: return emptyList()
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveServerList(servers: List<ServerConfig>) {
        prefs.edit().putString(Constants.SERVERS_KEY, adapter.toJson(servers)).apply()
    }
}
