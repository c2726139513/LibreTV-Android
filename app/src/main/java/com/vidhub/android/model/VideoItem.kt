package com.vidhub.android.model

data class VideoItem(
    val vodId: String,
    val title: String,
    val coverUrl: String?,
    val remarks: String?,
    val year: String?,
    val area: String?,
    val director: String?,
    val actor: String?,
    val typeName: String?,
    val description: String?,
    val playFrom: String?,
    val episodes: List<Episode> = emptyList()
)
