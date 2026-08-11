package com.sillytavern.eink.eink

import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView

interface EinkController {
    fun contentChanged(view: View)
    fun pageTurn(view: View)
    fun fullRefresh(view: View)
}

class GenericEinkController : EinkController {
    private val handler = Handler(Looper.getMainLooper())
    private var partialCount = 0
    private var pending: Runnable? = null

    override fun contentChanged(view: View) {
        pending?.let(handler::removeCallbacks)
        pending = Runnable {
            pending = null
            view.invalidate()
            partialCount++
            if (partialCount >= FULL_REFRESH_AFTER) fullRefresh(view)
        }.also { handler.postDelayed(it, CONTENT_BATCH_MS) }
    }

    override fun pageTurn(view: View) {
        view.invalidate()
        partialCount++
        if (partialCount >= FULL_REFRESH_AFTER) fullRefresh(view)
    }

    override fun fullRefresh(view: View) {
        pending?.let(handler::removeCallbacks)
        pending = null
        partialCount = 0
        view.invalidate()
    }

    /** Hardware page keys use a fixed viewport step so the layout never jumps. */
    fun pageUp(webView: WebView) = webView.evaluateJavascript("window.scrollBy(0, -Math.round(window.innerHeight * 0.85));", null)

    fun pageDown(webView: WebView) = webView.evaluateJavascript("window.scrollBy(0, Math.round(window.innerHeight * 0.85));", null)

    fun dispose() {
        pending?.let(handler::removeCallbacks)
        pending = null
        partialCount = 0
    }

    companion object {
        private const val CONTENT_BATCH_MS = 750L
        private const val FULL_REFRESH_AFTER = 8
    }
}
