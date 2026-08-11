package com.sillytavern.eink.model

/** The single server profile used by the browser shell. */
data class StoredProfile(
    val baseUrl: String,
    val handle: String = "",
    val allowPrivateLanHttp: Boolean = false,
)
