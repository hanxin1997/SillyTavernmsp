package com.sillytavern.eink.web

import android.webkit.PermissionRequest
import android.webkit.WebResourceRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

/** Delegates browser capabilities to the Activity without exposing broad native APIs. */
class SillyTavernWebChromeClient(
    private val onFileChooser: (ValueCallback<Array<android.net.Uri>>, WebChromeClient.FileChooserParams) -> Boolean,
    private val permissionHandler: (PermissionRequest) -> Unit,
    private val onProgress: (Int) -> Unit,
    private val onNewWindow: (android.net.Uri) -> Unit,
) : WebChromeClient() {
    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<android.net.Uri>>,
        fileChooserParams: WebChromeClient.FileChooserParams,
    ): Boolean = onFileChooser(filePathCallback, fileChooserParams)

    override fun onPermissionRequest(request: PermissionRequest) = permissionHandler(request)

    override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)

    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
        val child = WebView(view.context)
        var handled = false
        child.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(childView: WebView, request: WebResourceRequest): Boolean {
                if (!handled) onNewWindow(request.url)
                handled = true
                childView.destroy()
                return true
            }

            override fun onPageStarted(childView: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                if (!url.isNullOrBlank() && url != "about:blank") {
                    if (!handled) onNewWindow(android.net.Uri.parse(url))
                    handled = true
                    childView.stopLoading()
                    childView.destroy()
                }
            }
        }
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        transport.webView = child
        resultMsg.sendToTarget()
        return true
    }
}
