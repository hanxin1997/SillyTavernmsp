package com.sillytavern.eink.network

import android.webkit.CookieManager
import android.os.Handler
import android.os.Looper
import com.sillytavern.eink.model.StoredProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

sealed interface AuthenticationResult {
    data object Success : AuthenticationResult
    data object NoServerLoginRequired : AuthenticationResult
    data object BasicAuthRequired : AuthenticationResult
    data object InvalidCredentials : AuthenticationResult
}

class SessionAuthenticationException(message: String, cause: Throwable? = null) : IOException(message, cause)

fun interface SessionCookieWriter {
    fun write(origin: String, cookies: List<Cookie>)
}

private object WebViewCookieWriter : SessionCookieWriter {
    override fun write(origin: String, cookies: List<Cookie>) {
        val writeCookies = {
            val manager = CookieManager.getInstance()
            manager.setAcceptCookie(true)
            // Preserve expiry, path, Secure, HttpOnly and SameSite-compatible attributes.
            cookies.forEach { cookie -> manager.setCookie(origin, cookie.toString(), null) }
            manager.flush()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            writeCookies()
            return
        }
        val finished = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            runCatching { writeCookies() }.also { finished.countDown() }
        }
        check(finished.await(10, TimeUnit.SECONDS)) { "Cookie synchronization timed out." }
    }
}

/** Performs only SillyTavern's standard session login, then hands cookies to WebView. */
class WebSessionAuthenticator(
    private val cookieWriter: SessionCookieWriter = WebViewCookieWriter,
) {
    suspend fun authenticate(
        profile: StoredProfile,
        password: String,
        basicAuth: Pair<String, String>? = null,
    ): AuthenticationResult = withContext(Dispatchers.IO) {
        val origin = NetworkPolicy.validate(profile)
        if (origin.isPrivateLanHttp) NetworkPolicy.verifyPrivateDns(origin.uri.host)
        val jar = MemoryCookieJar()
        val client = OkHttpClient.Builder()
            .cookieJar(jar)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder().apply {
                    basicAuth?.let { (username, basicPassword) ->
                        header("Authorization", Credentials.basic(username, basicPassword))
                    }
                }.build()
                chain.proceed(request)
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        val csrfResponse = client.newCall(
            Request.Builder().url("${origin.value}/csrf-token").get().build(),
        ).execute()
        csrfResponse.use { response ->
            if (response.code == 401) return@withContext AuthenticationResult.BasicAuthRequired
            if (!response.isSuccessful) throw SessionAuthenticationException("无法连接服务器（HTTP ${response.code}）。")
            val token = runCatching { JSONObject(response.body?.string().orEmpty()).getString("token") }
                .getOrElse { throw SessionAuthenticationException("服务器返回了无效的 CSRF 响应。", it) }
            if (profile.handle.isBlank()) {
                syncCookies(origin.value, jar)
                return@withContext AuthenticationResult.NoServerLoginRequired
            }

            val body = JSONObject()
                .put("handle", profile.handle)
                .put("password", password)
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val loginResponse = client.newCall(
                Request.Builder()
                    .url("${origin.value}/api/users/login")
                    .header("Content-Type", "application/json")
                    .header("X-CSRF-Token", token)
                    .post(body)
                    .build(),
            ).execute()
            loginResponse.use { login ->
                if (login.code == 401) return@withContext AuthenticationResult.BasicAuthRequired
                if (login.code == 403) return@withContext AuthenticationResult.InvalidCredentials
                if (!login.isSuccessful) throw SessionAuthenticationException("登录失败（HTTP ${login.code}）。")
                val handle = runCatching { JSONObject(login.body?.string().orEmpty()).optString("handle") }
                    .getOrNull()
                if (handle.isNullOrBlank()) throw SessionAuthenticationException("服务器没有确认登录账号。")
            }
        }
        syncCookies(origin.value, jar)
        AuthenticationResult.Success
    }

    private fun syncCookies(origin: String, jar: MemoryCookieJar) {
        cookieWriter.write(origin, jar.cookiesForOrigin())
    }

    private class MemoryCookieJar : CookieJar {
        private val cookies = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(this.cookies) {
                this.cookies.removeAll { old -> cookies.any { it.name == old.name && it.domain == old.domain && it.path == old.path } }
                this.cookies.addAll(cookies)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookies) {
            cookies.filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }
        }

        fun cookiesForOrigin(): List<Cookie> = synchronized(cookies) { cookies.toList() }
    }
}
