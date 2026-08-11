package com.sillytavern.eink.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPolicyTest {
    @Test
    fun `https is accepted without LAN override`() {
        assertEquals("example.com", NetworkPolicy.normalizeBaseUrl("https://example.com").uri.host)
    }

    @Test
    fun `private LAN http requires explicit approval`() {
        assertThrows(IllegalArgumentException::class.java) {
            NetworkPolicy.normalizeBaseUrl("http://192.168.1.10:8000")
        }
        assertEquals("192.168.1.10", NetworkPolicy.normalizeBaseUrl("http://192.168.1.10:8000", true).uri.host)
    }

    @Test
    fun `public cleartext host is rejected even when override is enabled`() {
        listOf("example.com", "10.example.com", "192.168.example.com", "fcorp.com", "fdomain.com").forEach { host ->
            assertThrows(IllegalArgumentException::class.java) {
                NetworkPolicy.normalizeBaseUrl("http://$host", true)
            }
        }
    }

    @Test
    fun `missing scheme uses HTTPS for public hosts and HTTP for private hosts`() {
        assertEquals("https://example.com/", NetworkPolicy.normalizeBaseUrl("example.com").uri.toString())
        assertThrows(IllegalArgumentException::class.java) {
            NetworkPolicy.normalizeBaseUrl("192.168.1.10:8000")
        }
    }

    @Test
    fun `web app must be mounted at the server root`() {
        assertThrows(IllegalArgumentException::class.java) {
            NetworkPolicy.normalizeBaseUrl("https://example.com/sillytavern")
        }
    }

    @Test
    fun `recognizes private IPv4 and IPv6 ranges`() {
        assertTrue(NetworkPolicy.isPrivateHost("10.0.0.2"))
        assertTrue(NetworkPolicy.isPrivateHost("172.31.0.2"))
        assertTrue(NetworkPolicy.isPrivateHost("fd00::1"))
    }

    @Test
    fun `rejects malformed IPv4 literals`() {
        listOf("10.1", "10.0.0.999", "192.168.1.example").forEach {
            assertEquals(false, NetworkPolicy.isPrivateHost(it))
        }
    }
}
