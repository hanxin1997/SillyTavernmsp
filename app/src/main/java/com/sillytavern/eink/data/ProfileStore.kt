package com.sillytavern.eink.data

import android.content.Context
import com.sillytavern.eink.model.ServerProfile

class ProfileStore(context: Context) {
    private val preferences = context.getSharedPreferences("server_profile", Context.MODE_PRIVATE)

    fun load(): ServerProfile? {
        val baseUrl = preferences.getString("base_url", null) ?: return null
        return ServerProfile(
            baseUrl = baseUrl,
            handle = preferences.getString("handle", "").orEmpty(),
            source = preferences.getString("source", "openai").orEmpty(),
            model = preferences.getString("model", "").orEmpty(),
            allowPrivateLanHttp = preferences.getBoolean("allow_private_lan_http", false),
            contextTokens = preferences.getInt("context_tokens", 4095),
            responseTokens = preferences.getInt("response_tokens", 300),
        )
    }

    fun save(profile: ServerProfile) {
        preferences.edit()
            .putString("base_url", profile.baseUrl.trimEnd('/'))
            .putString("handle", profile.handle)
            .putString("source", profile.source)
            .putString("model", profile.model)
            .putBoolean("allow_private_lan_http", profile.allowPrivateLanHttp)
            .putInt("context_tokens", profile.contextTokens)
            .putInt("response_tokens", profile.responseTokens)
            .apply()
    }
}
