package com.sillytavern.eink.web

import android.net.Uri
import android.net.http.SslError
import android.webkit.HttpAuthHandler
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
/** Keeps page navigation constrained to the configured SillyTavern origin. */
class SillyTavernWebViewClient(
    private val trustedOrigin: Uri,
    private val onLoginRequired: () -> Unit,
    private val onExternalUrl: (Uri) -> Unit,
    private val onBasicAuth: (String, String, HttpAuthHandler) -> Unit,
    private val onPageState: (Boolean) -> Unit,
    private val onRendererGone: () -> Unit,
    private val onError: (String) -> Unit,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        val uri = request.url
        if (uri.scheme == "http" || uri.scheme == "https") {
            if (isTrusted(uri)) return false
            if (uri.scheme == "http") {
                if (request.hasGesture()) onExternalUrl(uri)
                return true
            }
            // User-initiated external links belong in the system browser. Redirects
            // stay in WebView so OAuth can return to the configured server.
            if (request.hasGesture()) onExternalUrl(uri)
            return request.hasGesture()
        }
        if (uri.scheme in setOf("file", "content", "data", "javascript")) return true
        onExternalUrl(uri)
        return true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
        onPageState(true)
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        onPageState(false)
        val uri = url?.let(Uri::parse) ?: return
        val path = uri.path.orEmpty()
        if (isTrusted(uri) && (path == "/login" || path == "/login.html")) onLoginRequired()
    }

    override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
        onBasicAuth(host, realm, handler)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        onError("证书验证失败，已拒绝连接。")
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) onError("网页加载失败：${error.description}")
        super.onReceivedError(view, request, error)
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        if (request.isForMainFrame && errorResponse.statusCode >= 500) {
            onError("服务器错误（HTTP ${errorResponse.statusCode}）。")
        }
        super.onReceivedHttpError(view, request, errorResponse)
    }

    override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
        onRendererGone()
        return true
    }

    private fun isTrusted(uri: Uri): Boolean =
        uri.scheme.equals(trustedOrigin.scheme, ignoreCase = true) &&
            uri.host.equals(trustedOrigin.host, ignoreCase = true) &&
            (uri.port.takeIf { it != -1 } ?: defaultPort(uri.scheme)) ==
            (trustedOrigin.port.takeIf { it != -1 } ?: defaultPort(trustedOrigin.scheme))

    private fun defaultPort(scheme: String?): Int = if (scheme.equals("https", ignoreCase = true)) 443 else 80
}
