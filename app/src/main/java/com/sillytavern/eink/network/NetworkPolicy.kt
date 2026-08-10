package com.sillytavern.eink.network

import com.sillytavern.eink.model.ServerProfile
import okhttp3.Dns
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException

object NetworkPolicy {
    fun validate(profile: ServerProfile): URI {
        val uri = runCatching { URI(profile.baseUrl.trim()) }.getOrElse { throw IllegalArgumentException("Invalid server URL.") }
        val scheme = uri.scheme?.lowercase()
        require(scheme == "https" || scheme == "http") { "Server URL must use HTTPS or HTTP." }
        require(!uri.host.isNullOrBlank()) { "Server URL must include a host." }
        if (scheme == "http") {
            require(profile.allowPrivateLanHttp) { "HTTP is disabled. Enable private LAN HTTP for a trusted local server." }
            require(isPrivateHost(uri.host)) { "Cleartext HTTP is only allowed for private LAN addresses." }
        }
        return uri
    }

    internal fun isPrivateHost(hostValue: String): Boolean {
        val host = hostValue.lowercase().removePrefix("[").removeSuffix("]")
        if (host == "localhost" || host.endsWith(".local")) return true
        val parts = host.split('.')
        if (parts.size == 4) {
            val octets = parts.map { it.toIntOrNull() ?: return false }
            if (octets.any { it !in 0..255 }) return false
            return octets[0] == 10 || octets[0] == 127 ||
                (octets[0] == 172 && octets[1] in 16..31) ||
                (octets[0] == 192 && octets[1] == 168)
        }
        if (!host.contains(':')) return false
        return runCatching { InetAddress.getByName(host) }.getOrNull()?.let(::isPrivateAddress) == true
    }

    internal fun isPrivateAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) return true
        val bytes = address.address
        return bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
    }
}

class PrivateLanDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (addresses.isEmpty() || addresses.any { !NetworkPolicy.isPrivateAddress(it) }) {
            throw UnknownHostException("Cleartext host did not resolve exclusively to private LAN addresses.")
        }
        return addresses
    }
}
