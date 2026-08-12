package com.sillytavern.eink.network

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.sillytavern.eink.model.ProxySettings
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class WebViewProxyException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Applies the process-wide WebView proxy and does not return until Chromium confirms it. */
class WebViewProxyManager(context: Context) {
    private val applicationContext = context.applicationContext

    suspend fun apply(settings: ProxySettings, currentServerHost: String) {
        val plan = runCatching { ProxyPolicy.createPlan(settings, currentServerHost) }
            .getOrElse { throw WebViewProxyException(it.message ?: "代理配置无效。", it) }

        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            if (plan.proxyUrl == null) return
            throw WebViewProxyException("当前系统 WebView 不支持应用内代理，请更新系统 WebView 或关闭代理。")
        }

        try {
            // ProxyController cannot cancel an operation. Keep it serialized even if an Activity is recreated,
            // so a late callback from an old screen can never overwrite the newest persisted configuration.
            withContext(NonCancellable) {
                operationMutex.withLock {
                    suspendCoroutine<Unit> { continuation ->
                    val callback = Runnable {
                        continuation.resume(Unit)
                    }
                    try {
                        val controller = ProxyController.getInstance()
                        val executor = ContextCompat.getMainExecutor(applicationContext)
                        if (plan.proxyUrl == null) {
                            // Clearing is required because the override belongs to the whole app process.
                            controller.clearProxyOverride(executor, callback)
                        } else {
                            val builder = ProxyConfig.Builder()
                                .addProxyRule(plan.proxyUrl, ProxyConfig.MATCH_ALL_SCHEMES)
                                // Replace WebView's implicit localhost bypass with our explicit server-host rule.
                                .removeImplicitRules()
                            plan.bypassRules.forEach { rule -> builder.addBypassRule(rule) }
                            controller.setProxyOverride(builder.build(), executor, callback)
                        }
                    } catch (error: IllegalArgumentException) {
                        continuation.resumeWithException(error)
                    } catch (error: UnsupportedOperationException) {
                        continuation.resumeWithException(error)
                    }
                    }
                }
            }
        } catch (error: WebViewProxyException) {
            throw error
        } catch (error: Throwable) {
            throw WebViewProxyException(error.message ?: "无法应用 WebView 代理。", error)
        }
    }

    companion object {
        private val operationMutex = Mutex()
    }
}
