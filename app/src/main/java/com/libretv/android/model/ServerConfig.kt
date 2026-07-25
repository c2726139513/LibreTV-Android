package com.libretv.android.model

data class ServerConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val password: String,
    val isActive: Boolean = false,
    val cmsSources: List<String> = emptyList(),
    val addedAt: Long = System.currentTimeMillis()
)
