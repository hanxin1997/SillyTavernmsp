package com.sillytavern.eink.data

import android.content.Context
import com.sillytavern.eink.eink.EinkThemeMode

data class EinkWebSettings(
    val themeMode: EinkThemeMode,
    val textZoom: Int,
)

/** Owns WebView display settings and performs the legacy Boolean migration once. */
class EinkSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): EinkWebSettings {
        val values = preferences.all
        val storedMode = values[KEY_THEME_MODE] as? String
        val legacyEnabled = values[LEGACY_KEY_ENABLED] as? Boolean
        val mode = EinkThemeMode.resolve(storedMode, legacyEnabled)
        val textZoom = (values[KEY_TEXT_ZOOM] as? Int)?.coerceIn(50, 200) ?: 100

        if (storedMode != mode.name || LEGACY_KEY_ENABLED in values) {
            preferences.edit()
                .putString(KEY_THEME_MODE, mode.name)
                .remove(LEGACY_KEY_ENABLED)
                .apply()
        }
        return EinkWebSettings(mode, textZoom)
    }

    fun save(settings: EinkWebSettings) {
        preferences.edit()
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putInt(KEY_TEXT_ZOOM, settings.textZoom.coerceIn(50, 200))
            .remove(LEGACY_KEY_ENABLED)
            .apply()
    }

    companion object {
        private const val PREFERENCES = "eink_web_settings"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val LEGACY_KEY_ENABLED = "enabled"
        private const val KEY_TEXT_ZOOM = "text_zoom"
    }
}
