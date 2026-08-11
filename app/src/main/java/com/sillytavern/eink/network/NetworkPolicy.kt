package com.sillytavern.eink.network

import com.sillytavern.eink.model.StoredProfile
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException

data class ServerOrigin(
    val uri: URI,
    val value: String,
    val isPrivateLanHttp: Boolean,
)

class PrivateLanHttpApprovalRequired(val origin: ServerOrigin) : IllegalArgumentException(
    "此服务器使用私网 HTTP，需要确认后才能连接。",
)

/** Centralizes URL normalization and the cleartext LAN policy used by both APIs and WebView. */
object NetworkPolicy {
    fun normalizeBaseUrl(input: String, allowPrivateLanHttp: Boolean = false): ServerOrigin {
        val trimmed = input.trim()
        require(trimmed.isNotBlank()) { "请输入服务器地址。" }
        val candidate = if ("://" in trimmed) trimmed else {
            val host = if (trimmed.startsWith('[')) {
                trimmed.substringAfter('[').substringBefore(']')
            } else {
                trimmed.substringBefore('/').substringBefore(':')
            }.lowercase()
            if (host == "localhost" || host.endsWith(".local") || isPrivateIpv4(host)) {
                "http://$trimmed"
            } else {
                "https://$trimmed"
            }
        }
        val parsed = runCatching { URI(candidate) }.getOrElse { throw IllegalArgumentException("服务器地址格式无效。") }
        val scheme = parsed.scheme?.lowercase()
        require(scheme == "https" || scheme == "http") { "服务器地址必须使用 HTTPS 或 HTTP。" }
        require(!parsed.host.isNullOrBlank()) { "服务器地址必须包含主机名。" }
        require(parsed.userInfo == null) { "服务器地址不能包含用户名或密码。" }
        require(parsed.query == null && parsed.fragment == null) { "服务器地址不能包含查询参数或片段。" }
        require(parsed.path.isNullOrBlank() || parsed.path == "/") { "服务器地址必须指向 SillyTavern 根地址。" }

        val normalizedUri = URI(scheme, null, parsed.host, parsed.port, "/", null, null)
        val privateHttp = scheme == "http"
        if (privateHttp) {
            require(isPrivateHost(parsed.host)) { "明文 HTTP 只允许 localhost 或私网地址。" }
            if (!allowPrivateLanHttp) throw PrivateLanHttpApprovalRequired(
                ServerOrigin(normalizedUri, normalizedUri.toString().trimEnd('/'), true),
            )
        }
        return ServerOrigin(normalizedUri, normalizedUri.toString().trimEnd('/'), privateHttp)
    }

    fun validate(profile: StoredProfile): ServerOrigin =
        normalizeBaseUrl(profile.baseUrl, profile.allowPrivateLanHttp)

    internal fun isPrivateHost(hostValue: String): Boolean {
        val host = hostValue.lowercase().removePrefix("[").removeSuffix("]")
        if (host == "localhost" || host.endsWith(".local")) return true
        if (isPrivateIpv4(host)) {
            val octets = host.split('.').map(String::toInt)
            return octets[0] == 10 || octets[0] == 127 ||
                (octets[0] == 172 && octets[1] in 16..31) ||
                (octets[0] == 192 && octets[1] == 168)
        }
        if (!host.contains(':')) return false
        return runCatching { InetAddress.getByName(host) }.getOrNull()?.let(::isPrivateAddress) == true
    }

    internal fun isPrivateIpv4(host: String): Boolean {
        val octets = host.split('.')
        return octets.size == 4 && octets.all { it.toIntOrNull()?.let { value -> value in 0..255 } == true }
    }

    internal fun isPrivateAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) return true
        val bytes = address.address
        return bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
    }

    fun verifyPrivateDns(host: String) {
        val addresses = runCatching { InetAddress.getAllByName(host).toList() }
            .getOrElse { throw UnknownHostException("无法解析服务器地址。") }
        require(addresses.isNotEmpty() && addresses.all(::isPrivateAddress)) {
            "私网 HTTP 主机解析到了非私网地址，已拒绝连接。"
        }
    }
}
