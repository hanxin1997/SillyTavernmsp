package com.sillytavern.eink.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sillytavern.eink.eink.EinkThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EinkSettingsStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: EinkSettingsStore

    @Before
    fun setUp() {
        context.getSharedPreferences("eink_web_settings", Context.MODE_PRIVATE).edit().clear().commit()
        store = EinkSettingsStore(context)
    }

    @Test
    fun legacyFalseMigratesToOffAndPreservesTextZoom() {
        val preferences = context.getSharedPreferences("eink_web_settings", Context.MODE_PRIVATE)
        preferences.edit().putBoolean("enabled", false).putInt("text_zoom", 120).commit()

        val settings = store.load()

        assertEquals(EinkThemeMode.OFF, settings.themeMode)
        assertEquals(120, settings.textZoom)
        assertEquals("OFF", preferences.getString("theme_mode", null))
        assertFalse(preferences.contains("enabled"))
    }
}
