package com.vidhub.android.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CustomSource(
    @Json(name = "name") val name: String,
    @Json(name = "url") val url: String,
    @Json(name = "detail") val detail: String? = null,
)
