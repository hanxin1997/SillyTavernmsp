package com.sillytavern.eink.eink

import android.content.Context
import android.util.Base64
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.sillytavern.eink.R

/** Injects app-local styles without changing the user's server-side SillyTavern theme. */
class EinkStyleInjector(
    private val context: Context,
    private val allowedOrigin: String,
) {
    private var documentStartHandler: ScriptHandler? = null

    fun registerForFutureDocuments(webView: WebView, mode: EinkThemeMode) {
        documentStartHandler?.remove()
        documentStartHandler = null
        if (mode == EinkThemeMode.OFF) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return

        documentStartHandler = WebViewCompat.addDocumentStartJavaScript(
            webView,
            installScript(mode),
            setOf(allowedOrigin),
        )
    }

    fun applyToCurrentDocument(webView: WebView, mode: EinkThemeMode) {
        val script = if (mode == EinkThemeMode.OFF) REMOVE_SCRIPT else installScript(mode)
        webView.evaluateJavascript(script, null)
    }

    fun dispose() {
        documentStartHandler?.remove()
        documentStartHandler = null
    }

    private fun installScript(mode: EinkThemeMode): String {
        val rawResource = when (mode) {
            EinkThemeMode.OFF -> error("OFF mode has no stylesheet")
            EinkThemeMode.BALANCED -> R.raw.eink
            EinkThemeMode.HIGH_CONTRAST -> R.raw.eink_high_contrast
        }
        val css = context.resources.openRawResource(rawResource).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val encoded = Base64.encodeToString(css.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val modeName = mode.name.lowercase()
        return """
            (function(){
              var apply=function(){
                var root=document.documentElement;
                if(!root)return false;
                root.setAttribute('data-st-eink-mode','$modeName');
                var style=document.getElementById('st-eink-style');
                if(!style){style=document.createElement('style');style.id='st-eink-style';(document.head||root).appendChild(style);}
                style.textContent=atob('$encoded');
                return true;
              };
              if(!apply()){
                var observer=new MutationObserver(function(){if(apply())observer.disconnect();});
                observer.observe(document,{childList:true,subtree:true});
              }
            })();
        """.trimIndent()
    }

    companion object {
        private const val REMOVE_SCRIPT = """
            (function(){
              var root=document.documentElement;
              if(root)root.removeAttribute('data-st-eink-mode');
              var style=document.getElementById('st-eink-style');
              if(style)style.remove();
            })();
        """
    }
}
