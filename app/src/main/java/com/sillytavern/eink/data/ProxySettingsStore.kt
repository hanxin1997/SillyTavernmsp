package com.sillytavern.eink.data

import android.content.Context
import com.sillytavern.eink.model.ProxySettings
import com.sillytavern.eink.model.ProxyType

/** Stores only non-secret proxy fields. Authenticated proxies are intentionally unsupported. */
class ProxySettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): ProxySettings {
        val enabled = preferences.getBoolean(KEY_ENABLED, false)
        val type = ProxyType.fromPersisted(preferences.getString(KEY_TYPE, null))
        return ProxySettings(
            enabled = enabled,
            type = type,
            host = preferences.getString(KEY_HOST, DEFAULT_HOST).orEmpty(),
            port = preferences.getInt(KEY_PORT, type.defaultPort),
        )
    }

    fun save(settings: ProxySettings) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_TYPE, settings.type.persistedValue)
            .putString(KEY_HOST, settings.host)
            .putInt(KEY_PORT, settings.port)
            .remove(LEGACY_KEY_BYPASS_SERVER)
            .apply()
    }

    companion object {
        private const val PREFERENCES = "browser_proxy"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TYPE = "type"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val LEGACY_KEY_BYPASS_SERVER = "bypass_current_server"
        private const val DEFAULT_HOST = "127.0.0.1"
    }
}
