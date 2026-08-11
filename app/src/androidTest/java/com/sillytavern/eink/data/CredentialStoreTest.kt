package com.sillytavern.eink.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sillytavern.eink.model.StoredProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: CredentialStore

    @Before
    fun setUp() {
        context.getSharedPreferences("server_profile", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("browser_credentials", Context.MODE_PRIVATE).edit().clear().commit()
        store = CredentialStore(context)
    }

    @Test
    fun passwordRoundTripsWithoutPlaintextPreferences() {
        store.saveProfile(StoredProfile("https://example.com", "alice"))
        store.savePassword("very-secret-password")

        assertEquals("very-secret-password", store.loadPassword())
        val raw = context.getSharedPreferences("browser_credentials", Context.MODE_PRIVATE).all.values.joinToString()
        assertFalse(raw.contains("very-secret-password"))
    }

    @Test
    fun legacyProfileIsMigratedAndObsoleteValuesAreRemoved() {
        context.getSharedPreferences("server_profile", Context.MODE_PRIVATE).edit()
            .putString("base_url", "http://192.168.1.20:8000")
            .putString("handle", "legacy-user")
            .putBoolean("allow_private_lan_http", true)
            .putString("model", "obsolete")
            .commit()

        val profile = store.loadProfile()

        assertEquals("legacy-user", profile?.handle)
        assertEquals(true, profile?.allowPrivateLanHttp)
        assertFalse(context.getSharedPreferences("server_profile", Context.MODE_PRIVATE).contains("base_url"))
    }
}
