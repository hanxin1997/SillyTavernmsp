package com.sillytavern.eink.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sillytavern.eink.model.ProxySettings
import com.sillytavern.eink.model.ProxyType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProxySettingsStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: ProxySettingsStore

    @Before
    fun setUp() {
        context.getSharedPreferences("browser_proxy", Context.MODE_PRIVATE).edit().clear().commit()
        store = ProxySettingsStore(context)
    }

    @Test
    fun unauthenticatedSocks5SettingsRoundTrip() {
        val expected = ProxySettings(true, ProxyType.SOCKS5, "127.0.0.1", 1080)

        store.save(expected)

        assertEquals(expected, store.load())
    }
}
