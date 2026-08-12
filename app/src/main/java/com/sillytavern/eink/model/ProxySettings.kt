package com.sillytavern.eink.model

/** Proxy protocols exposed by Android WebView's process-level proxy override. */
enum class ProxyType(
    val persistedValue: String,
    val browserScheme: String,
    val defaultPort: Int,
) {
    HTTP("http", "http", 8080),
    HTTPS("https", "https", 8443),
    // Chromium names an unauthenticated SOCKS5 endpoint with the socks:// scheme.
    SOCKS5("socks5", "socks", 1080),
    ;

    companion object {
        fun fromPersisted(value: String?): ProxyType = entries.firstOrNull {
            it.persistedValue.equals(value, ignoreCase = true)
        } ?: SOCKS5
    }
}

/** App-local, unauthenticated proxy configuration. */
data class ProxySettings(
    val enabled: Boolean,
    val type: ProxyType,
    val host: String,
    val port: Int,
) {
    companion object {
        fun disabled() = ProxySettings(
            enabled = false,
            type = ProxyType.SOCKS5,
            host = "127.0.0.1",
            port = ProxyType.SOCKS5.defaultPort,
        )
    }
}
