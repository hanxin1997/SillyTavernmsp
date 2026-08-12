package com.sillytavern.eink.network

import com.sillytavern.eink.model.ProxySettings
import com.sillytavern.eink.model.ProxyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ProxyPolicyTest {
    @Test
    fun `disabled proxy clears the WebView override`() {
        val plan = ProxyPolicy.createPlan(ProxySettings.disabled(), "192.168.1.20")

        assertNull(plan.proxyUrl)
        assertEquals(emptyList<String>(), plan.bypassRules)
    }

    @Test
    fun `SOCKS5 uses the Chromium socks URI scheme`() {
        val plan = ProxyPolicy.createPlan(
            ProxySettings(true, ProxyType.SOCKS5, "127.0.0.1", 1080),
            "192.168.1.20",
        )

        assertEquals("socks://127.0.0.1:1080", plan.proxyUrl)
        assertEquals(listOf("192.168.1.20"), plan.bypassRules)
    }

    @Test
    fun `HTTP and HTTPS preserve their proxy transport schemes`() {
        val http = ProxyPolicy.createPlan(
            ProxySettings(true, ProxyType.HTTP, "proxy.local", 8080),
            "server.local",
        )
        val https = ProxyPolicy.createPlan(
            ProxySettings(true, ProxyType.HTTPS, "proxy.local", 8443),
            "server.local",
        )

        assertEquals("http://proxy.local:8080", http.proxyUrl)
        assertEquals("https://proxy.local:8443", https.proxyUrl)
        assertEquals(listOf("server.local"), http.bypassRules)
    }

    @Test
    fun `IPv6 proxy hosts are bracketed exactly once`() {
        val raw = ProxyPolicy.createPlan(
            ProxySettings(true, ProxyType.SOCKS5, "::1", 1080),
            "server.local",
        )
        val bracketed = ProxyPolicy.createPlan(
            ProxySettings(true, ProxyType.SOCKS5, "[::1]", 1080),
            "server.local",
        )

        assertEquals("socks://[::1]:1080", raw.proxyUrl)
        assertEquals(raw.proxyUrl, bracketed.proxyUrl)
    }

    @Test
    fun `proxy endpoint rejects credentials paths and invalid ports`() {
        listOf(
            ProxySettings(true, ProxyType.SOCKS5, "user@127.0.0.1", 1080),
            ProxySettings(true, ProxyType.SOCKS5, "127.0.0.1/path", 1080),
            ProxySettings(true, ProxyType.SOCKS5, "127.0.0.1", 0),
            ProxySettings(true, ProxyType.SOCKS5, "127.0.0.1", 65_536),
        ).forEach { settings ->
            assertThrows(IllegalArgumentException::class.java) {
                ProxyPolicy.createPlan(settings, "server.local")
            }
        }
    }

    @Test
    fun `proxy endpoint rejects malformed IPv6`() {
        listOf("::::", "::%", "[::1").forEach { host ->
            assertThrows(IllegalArgumentException::class.java) {
                ProxyPolicy.createPlan(ProxySettings(true, ProxyType.SOCKS5, host, 1080), "server.local")
            }
        }
    }
}
