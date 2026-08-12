package com.sillytavern.eink.network

import com.sillytavern.eink.model.ProxySettings
import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress

data class BrowserProxyPlan(
    val proxyUrl: String?,
    val bypassRules: List<String>,
)

/** Pure validation and mapping shared by the settings UI, tests, and WebView adapter. */
object ProxyPolicy {
    fun createPlan(settings: ProxySettings, currentServerHost: String): BrowserProxyPlan {
        if (!settings.enabled) return BrowserProxyPlan(proxyUrl = null, bypassRules = emptyList())

        require(settings.port in 1..65_535) { "代理端口必须在 1 到 65535 之间。" }
        val host = normalizeHost(settings.host)
        val authorityHost = if (':' in host) "[$host]" else host
        // Session login is direct, so the WebView must also keep this host direct.
        val bypassRules = if (currentServerHost.isNotBlank()) {
            listOf(currentServerHost.removePrefix("[").removeSuffix("]"))
        } else {
            emptyList()
        }
        return BrowserProxyPlan(
            proxyUrl = "${settings.type.browserScheme}://$authorityHost:${settings.port}",
            bypassRules = bypassRules,
        )
    }

    fun normalize(settings: ProxySettings): ProxySettings {
        if (!settings.enabled) return settings
        val plan = createPlan(settings, "")
        checkNotNull(plan.proxyUrl)
        return settings.copy(host = normalizeHost(settings.host))
    }

    private fun normalizeHost(input: String): String {
        val trimmed = input.trim()
        require(trimmed.isNotBlank()) { "请输入代理主机。" }
        require(trimmed.none { it.isWhitespace() } && trimmed.none { it.isISOControl() }) { "代理主机不能包含空白字符。" }
        require(trimmed.none { it in "@/\\?#" }) { "代理主机只能填写主机名或 IP 地址，不能包含认证信息、路径或参数。" }

        val hasOpeningBracket = trimmed.startsWith('[')
        val hasClosingBracket = trimmed.endsWith(']')
        require(hasOpeningBracket == hasClosingBracket) { "IPv6 代理地址的方括号不完整。" }
        val unwrapped = if (hasOpeningBracket) trimmed.substring(1, trimmed.length - 1) else trimmed
        require(unwrapped.isNotBlank()) { "请输入代理主机。" }

        if (':' in unwrapped) {
            require('%' !in unwrapped && unwrapped.matches(Regex("[0-9A-Fa-f:.]+"))) {
                "IPv6 代理地址格式无效。"
            }
            require(runCatching { InetAddress.getByName(unwrapped) is Inet6Address }.getOrDefault(false)) {
                "IPv6 代理地址格式无效。"
            }
            return unwrapped.lowercase()
        }

        val ascii = runCatching { IDN.toASCII(unwrapped) }
            .getOrElse { throw IllegalArgumentException("代理主机格式无效。") }
            .lowercase()
        require(ascii.length <= 253 && ascii.matches(Regex("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?"))) {
            "代理主机格式无效。"
        }
        require(ascii.split('.').all { it.isNotEmpty() && it.length <= 63 && !it.startsWith('-') && !it.endsWith('-') }) {
            "代理主机格式无效。"
        }
        return ascii
    }
}
