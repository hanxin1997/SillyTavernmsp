package com.sillytavern.eink.ui

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.sillytavern.eink.R
import com.sillytavern.eink.data.CredentialStore
import com.sillytavern.eink.data.EinkSettingsStore
import com.sillytavern.eink.data.EinkWebSettings
import com.sillytavern.eink.data.ProxySettingsStore
import com.sillytavern.eink.databinding.ActivityWebAppBinding
import com.sillytavern.eink.eink.EinkStyleInjector
import com.sillytavern.eink.eink.EinkThemeMode
import com.sillytavern.eink.eink.GenericEinkController
import com.sillytavern.eink.model.StoredProfile
import com.sillytavern.eink.network.AuthenticationResult
import com.sillytavern.eink.network.NetworkPolicy
import com.sillytavern.eink.network.SessionAuthenticationException
import com.sillytavern.eink.network.WebViewProxyException
import com.sillytavern.eink.network.WebViewProxyManager
import com.sillytavern.eink.network.WebSessionAuthenticator
import com.sillytavern.eink.web.SillyTavernWebChromeClient
import com.sillytavern.eink.web.SillyTavernWebViewClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import org.json.JSONObject

/** Browser shell for the complete SillyTavern web application. */
class WebAppActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWebAppBinding
    private lateinit var webView: WebView
    private lateinit var profile: StoredProfile
    private lateinit var origin: Uri
    private lateinit var credentialStore: CredentialStore
    private lateinit var proxySettingsStore: ProxySettingsStore
    private lateinit var einkSettingsStore: EinkSettingsStore
    private lateinit var proxyManager: WebViewProxyManager
    private lateinit var einkStyleInjector: EinkStyleInjector
    private val authenticator = WebSessionAuthenticator()
    private val einkController = GenericEinkController()
    private var authInProgress = false
    private var pageError = false
    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermission: PermissionRequest? = null
    private var activeBasicAuth: Pair<String, String>? = null
    private val attemptedBasicRealms = mutableSetOf<String>()
    private var browserReady = false
    private var einkMode = EinkThemeMode.BALANCED
    private var textZoom = 100
    private val blobDownloadBridge by lazy {
        BlobDownloadBridge(this) { message ->
            runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebAppBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBars(binding.webRoot)
        credentialStore = CredentialStore(this)
        proxySettingsStore = ProxySettingsStore(this)
        einkSettingsStore = EinkSettingsStore(this)
        proxyManager = WebViewProxyManager(this)
        val stored = credentialStore.loadProfile()
        if (stored == null) {
            returnToLogin()
            return
        }
        profile = stored
        val validated = runCatching { NetworkPolicy.validate(profile) }.getOrElse {
            returnToLogin()
            return
        }
        origin = Uri.parse(validated.value)
        val settings = einkSettingsStore.load()
        einkMode = settings.themeMode
        textZoom = settings.textZoom
        webView = binding.webView
        einkStyleInjector = EinkStyleInjector(this, trustedOriginRule())
        configureToolbar()
        binding.loadingStatus.text = getString(R.string.saved_credentials)
        binding.loadingStatus.visibility = View.VISIBLE
        lifecycleScope.launch {
            bootstrapWebView(savedInstanceState)
        }
    }

    private suspend fun bootstrapWebView(savedInstanceState: Bundle?) {
        try {
            // ProxyController is asynchronous and process-wide: no WebView request may start before this returns.
            proxyManager.apply(proxySettingsStore.load(), origin.host.orEmpty())
            configureWebView()
            if (profile.allowPrivateLanHttp) {
                withContext(Dispatchers.IO) { NetworkPolicy.verifyPrivateDns(origin.host.orEmpty()) }
            }
            browserReady = true
            updateNavigationButtons()
            if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
                webView.loadUrl(profile.baseUrl)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: WebViewProxyException) {
            showError(error.message ?: getString(R.string.connection_failed))
        } catch (error: Throwable) {
            showError(error.message ?: getString(R.string.connection_failed))
        }
    }

    private fun configureWebView() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) WebView.startSafeBrowsing(this, null)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            builtInZoomControls = false
            displayZoomControls = false
            textZoom = this@WebAppActivity.textZoom
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            // The system picker returns permission-scoped content:// URIs.
            allowContentAccess = true
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            // The app supplies deterministic paper colors; OEM darkening must not invert them again.
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, false)
        }
        webView.isVerticalScrollBarEnabled = true
        einkStyleInjector.registerForFutureDocuments(webView, einkMode)
        configureBlobDownloadBridge()
        webView.webViewClient = SillyTavernWebViewClient(
            trustedOrigin = origin,
            onLoginRequired = ::loginIfPossible,
            onExternalUrl = ::openExternal,
            onBasicAuth = ::handleBasicAuth,
            onPageState = { loading ->
                if (loading) {
                    pageError = false
                    binding.loadingStatus.text = getString(R.string.loading)
                }
                binding.loadingStatus.visibility = if (loading || pageError) View.VISIBLE else View.GONE
                if (!loading) injectEinkStyles()
                einkController.contentChanged(webView)
                updateNavigationButtons()
            },
            onRendererGone = ::handleRendererGone,
            onError = ::showError,
        )
        webView.webChromeClient = SillyTavernWebChromeClient(
            onFileChooser = ::showFileChooser,
            permissionHandler = ::handlePermissionRequest,
            onProgress = { progress ->
                if (progress >= 100 && !authInProgress && !pageError) binding.loadingStatus.visibility = View.GONE
            },
            onNewWindow = ::openNewWindow,
        )
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            enqueueDownload(url, userAgent, contentDisposition, mimeType)
        }
    }

    private fun configureToolbar() = with(binding) {
        back.setOnClickListener { if (browserReady && webView.canGoBack()) webView.goBack() }
        forward.setOnClickListener { if (browserReady && webView.canGoForward()) webView.goForward() }
        reload.setOnClickListener { if (browserReady) webView.reload() }
        home.setOnClickListener { if (browserReady) webView.loadUrl(profile.baseUrl) }
        settings.setOnClickListener { showSettings() }
        updateNavigationButtons()
    }

    private fun configureBlobDownloadBridge() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(
            webView,
            "EinkBlobDownload",
            setOf(trustedOriginRule()),
        ) { _, message, sourceOrigin, isMainFrame, _ ->
            if (isMainFrame && isTrustedOrigin(sourceOrigin)) {
                runCatching {
                    val payload = JSONObject(message.data.orEmpty())
                    blobDownloadBridge.save(
                        payload.optString("filename", "download"),
                        payload.optString("mimeType", "application/octet-stream"),
                        payload.getString("encoded"),
                    )
                }
            }
        }
    }

    private fun loginIfPossible() {
        if (authInProgress) return
        val password = credentialStore.loadPassword()
        if (profile.handle.isBlank() || password == null) {
            showError(getString(R.string.login_expired))
            return
        }
        authInProgress = true
        binding.loadingStatus.text = getString(R.string.auto_login)
        binding.loadingStatus.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                when (authenticator.authenticate(profile, password, activeBasicAuth)) {
                    AuthenticationResult.Success,
                    AuthenticationResult.NoServerLoginRequired,
                    -> webView.loadUrl(profile.baseUrl)
                    AuthenticationResult.BasicAuthRequired -> showError(getString(R.string.basic_auth_first))
                    AuthenticationResult.InvalidCredentials -> {
                        credentialStore.clearPassword()
                        returnToLogin()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: SessionAuthenticationException) {
                showError(error.message ?: getString(R.string.connection_failed))
            } finally {
                authInProgress = false
            }
        }
    }

    private fun handleBasicAuth(host: String, realm: String, handler: HttpAuthHandler) {
        if (!host.equals(origin.host, ignoreCase = true)) {
            handler.cancel()
            return
        }
        val challengeKey = "${host.lowercase()}\u0000$realm"
        credentialStore.loadBasicAuth(host, realm)?.let { (username, password) ->
            if (attemptedBasicRealms.add(challengeKey)) {
                activeBasicAuth = username to password
                handler.proceed(username, password)
                return
            }
            credentialStore.clearBasicAuth(host, realm)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
        }
        val username = EditText(this).apply { hint = getString(R.string.basic_username) }
        val password = EditText(this).apply { hint = getString(R.string.basic_password); inputType = 0x81 }
        val remember = CheckBox(this).apply { text = getString(R.string.remember_basic); isChecked = true }
        content.addView(username)
        content.addView(password)
        content.addView(remember)
        AlertDialog.Builder(this)
            .setTitle(R.string.basic_auth_title)
            .setView(content)
            .setNegativeButton(R.string.cancel) { _, _ -> handler.cancel() }
            .setPositiveButton(R.string.connect) { _, _ ->
                val user = username.text.toString()
                val pass = password.text.toString()
                attemptedBasicRealms.add(challengeKey)
                activeBasicAuth = user to pass
                if (remember.isChecked) credentialStore.saveBasicAuth(host, realm, user, pass)
                handler.proceed(user, pass)
            }
            .setOnCancelListener { handler.cancel() }
            .show()
    }

    private fun showFileChooser(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        pendingFileCallback?.onReceiveValue(null)
        pendingFileCallback = callback
        return runCatching {
            startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST)
            true
        }.getOrElse {
            pendingFileCallback = null
            false
        }
    }

    private fun handlePermissionRequest(request: PermissionRequest) {
        if (!isTrustedOrigin(request.origin)) {
            request.deny()
            return
        }
        val permissions = buildList {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in request.resources) add(Manifest.permission.RECORD_AUDIO)
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in request.resources) add(Manifest.permission.CAMERA)
        }
        if (permissions.isEmpty()) {
            request.deny()
            return
        }
        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            request.grant(request.resources)
        } else {
            pendingPermission = request
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST)
        }
    }

    private fun enqueueDownload(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        if (url.startsWith("blob:", ignoreCase = true)) return
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(contentDisposition ?: getString(R.string.download_title))
            setMimeType(mimeType)
            addRequestHeader("User-Agent", userAgent)
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(
                this@WebAppActivity,
                Environment.DIRECTORY_DOWNLOADS,
                URLUtil.guessFileName(url, contentDisposition, mimeType),
            )
        }
        getSystemService(DownloadManager::class.java).enqueue(request)
        Toast.makeText(this, getString(R.string.download_saved), Toast.LENGTH_SHORT).show()
    }

    private fun showSettings() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 4, 32, 0)
        }
        content.addView(TextView(this).apply { text = getString(R.string.eink_theme); setPadding(0, 8, 0, 4) })
        val themes = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val themeOff = RadioButton(this).apply { text = getString(R.string.eink_theme_off); id = View.generateViewId() }
        val themeBalanced = RadioButton(this).apply { text = getString(R.string.eink_theme_balanced); id = View.generateViewId() }
        val themeHighContrast = RadioButton(this).apply {
            text = getString(R.string.eink_theme_high_contrast)
            id = View.generateViewId()
        }
        themes.addView(themeOff)
        themes.addView(themeBalanced)
        themes.addView(themeHighContrast)
        themes.check(
            when (einkMode) {
                EinkThemeMode.OFF -> themeOff.id
                EinkThemeMode.BALANCED -> themeBalanced.id
                EinkThemeMode.HIGH_CONTRAST -> themeHighContrast.id
            },
        )
        content.addView(themes)
        content.addView(TextView(this).apply { text = getString(R.string.font_size); setPadding(0, 16, 0, 4) })
        val sizes = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val small = RadioButton(this).apply { text = getString(R.string.font_size_small); id = View.generateViewId() }
        val normal = RadioButton(this).apply { text = getString(R.string.font_size_normal); id = View.generateViewId() }
        val large = RadioButton(this).apply { text = getString(R.string.font_size_large); id = View.generateViewId() }
        sizes.addView(small); sizes.addView(normal); sizes.addView(large)
        when (textZoom) { in 0..90 -> sizes.check(small.id); in 111..200 -> sizes.check(large.id); else -> sizes.check(normal.id) }
        content.addView(sizes)
        fun persistDisplayDraft() {
            einkMode = when (themes.checkedRadioButtonId) {
                themeOff.id -> EinkThemeMode.OFF
                themeHighContrast.id -> EinkThemeMode.HIGH_CONTRAST
                else -> EinkThemeMode.BALANCED
            }
            textZoom = when (sizes.checkedRadioButtonId) { small.id -> 90; large.id -> 120; else -> 100 }
            einkSettingsStore.save(EinkWebSettings(einkMode, textZoom))
            webView.settings.textZoom = textZoom
            einkStyleInjector.registerForFutureDocuments(webView, einkMode)
            injectEinkStyles()
        }
        var settingsDialog: AlertDialog? = null
        content.addView(Button(this).apply {
            text = getString(R.string.proxy_settings)
            setOnClickListener {
                ProxySettingsDialog.show(this@WebAppActivity, proxySettingsStore.load()) { proxy ->
                    persistDisplayDraft()
                    proxySettingsStore.save(proxy)
                    settingsDialog?.dismiss()
                    // Recreating destroys old WebView sockets before the process-wide override changes.
                    recreate()
                }
            }
        })
        content.addView(Button(this).apply {
            text = getString(R.string.logout)
            setOnClickListener { logout() }
        })
        content.addView(Button(this).apply {
            text = getString(R.string.switch_server)
            setOnClickListener { clearCredentials() }
        })
        val scroll = ScrollView(this).apply { addView(content) }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setView(scroll)
            .setNeutralButton(R.string.full_refresh, null)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> persistDisplayDraft() }
            .create()
        settingsDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                persistDisplayDraft()
                einkController.fullRefresh(webView)
            }
        }
        dialog.show()
    }

    private fun clearCredentials() {
        credentialStore.clearAll()
        clearWebData()
        returnToLogin()
    }

    private fun logout() {
        credentialStore.clearPassword()
        clearWebData()
        returnToLogin()
    }

    private fun clearWebData() {
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
        WebStorage.getInstance().deleteAllData()
        webView.clearCache(true)
        webView.clearHistory()
    }

    private fun injectEinkStyles() {
        val current = webView.url?.let(Uri::parse) ?: return
        if (!isTrustedOrigin(current)) return
        webView.evaluateJavascript(BLOB_DOWNLOAD_SCRIPT, null)
        einkStyleInjector.applyToCurrentDocument(webView, einkMode)
    }

    private fun updateNavigationButtons() = with(binding) {
        back.isEnabled = browserReady && webView.canGoBack()
        forward.isEnabled = browserReady && webView.canGoForward()
        reload.isEnabled = browserReady
        home.isEnabled = browserReady
    }

    private fun showError(message: String) {
        pageError = true
        binding.loadingStatus.text = message
        binding.loadingStatus.visibility = View.VISIBLE
    }

    private fun handleRendererGone() {
        showError(getString(R.string.renderer_crashed))
        binding.reload.setOnClickListener { recreate() }
    }

    private fun openExternal(uri: Uri) {
        if (uri.scheme in setOf("file", "content", "data", "javascript")) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            .onFailure { showError(getString(R.string.no_link_handler)) }
    }

    private fun openNewWindow(uri: Uri) {
        if (isTrustedOrigin(uri)) webView.loadUrl(uri.toString()) else openExternal(uri)
    }

    private fun isTrustedOrigin(uri: Uri): Boolean {
        val currentPort = uri.port.takeIf { it != -1 } ?: if (uri.scheme == "https") 443 else 80
        val originPort = origin.port.takeIf { it != -1 } ?: if (origin.scheme == "https") 443 else 80
        return uri.scheme.equals(origin.scheme, true) && uri.host.equals(origin.host, true) && currentPort == originPort
    }

    private fun trustedOriginRule(): String = "${origin.scheme}://${origin.encodedAuthority}"

    private fun returnToLogin() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_FORCE_LOGIN, true)
        })
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != FILE_CHOOSER_REQUEST) return
        val callback = pendingFileCallback ?: return
        pendingFileCallback = null
        val uris = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
        callback.onReceiveValue(uris)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST) return
        val request = pendingPermission ?: return
        pendingPermission = null
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            request.grant(request.resources)
        } else {
            request.deny()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_PAGE_UP -> { einkController.pageUp(webView); return true }
            KeyEvent.KEYCODE_PAGE_DOWN -> { einkController.pageDown(webView); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::webView.isInitialized && browserReady) webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onBackPressed() {
        if (browserReady && webView.canGoBack()) webView.goBack() else super.onBackPressed()
        updateNavigationButtons()
    }

    override fun onDestroy() {
        einkController.dispose()
        if (::einkStyleInjector.isInitialized) einkStyleInjector.dispose()
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private class BlobDownloadBridge(
        private val context: Context,
        private val onSaved: (String) -> Unit,
    ) {
        fun save(filename: String, mimeType: String, encoded: String) {
            Thread {
                runCatching {
                    require(encoded.length <= MAX_BLOB_BASE64_LENGTH) { "blob too large" }
                    val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: error("download directory unavailable")
                    directory.mkdirs()
                    val fallbackName = if (mimeType.contains("json", ignoreCase = true)) "download.json" else "download"
                    val safeName = File(filename.ifBlank { fallbackName }).name
                        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        .take(160)
                    FileOutputStream(File(directory, safeName)).use { it.write(Base64.decode(encoded, Base64.DEFAULT)) }
                    onSaved(context.getString(R.string.download_saved))
                }.onFailure { onSaved(context.getString(R.string.download_failed)) }
            }.start()
        }
    }

    companion object {
        private const val FILE_CHOOSER_REQUEST = 4101
        private const val PERMISSION_REQUEST = 4102
        private const val MAX_BLOB_BASE64_LENGTH = 64 * 1024 * 1024
        private val BLOB_DOWNLOAD_SCRIPT = """
            (function(){if(window.__stEinkDownloadHook)return;window.__stEinkDownloadHook=true;
            document.addEventListener('click',function(e){var a=e.target.closest&&e.target.closest('a[download]');
            if(!a||!a.href||a.href.indexOf('blob:')!==0||!window.EinkBlobDownload)return;e.preventDefault();e.stopPropagation();
            fetch(a.href).then(function(r){return r.blob()}).then(function(b){var f=new FileReader();
            f.onloadend=function(){window.EinkBlobDownload.postMessage(JSON.stringify({filename:a.download||'download',mimeType:b.type||'application/octet-stream',encoded:String(f.result).split(',')[1]}));};f.readAsDataURL(b);});},true);})();
        """.trimIndent()
    }
}
