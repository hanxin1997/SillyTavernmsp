package com.sillytavern.eink.network

import com.sillytavern.eink.model.StoredProfile
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebSessionAuthenticatorTest {
    private lateinit var server: MockWebServer
    private val writtenCookies = mutableListOf<Cookie>()
    private val authenticator = WebSessionAuthenticator { _, cookies -> writtenCookies.addAll(cookies) }

    @Before
    fun setUp() {
        writtenCookies.clear()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `csrf cookie is carried into login and exported to WebView`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Set-Cookie", "session=anonymous; Path=/")
                .setBody("""{"token":"csrf-token"}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Set-Cookie", "session=authenticated; Path=/")
                .setBody("""{"handle":"alice"}"""),
        )

        val result = authenticator.authenticate(profile("alice"), "secret")

        assertEquals(AuthenticationResult.Success, result)
        assertEquals("/csrf-token", server.takeRequest().path)
        val login = server.takeRequest()
        assertEquals("/api/users/login", login.path)
        assertEquals("csrf-token", login.getHeader("X-CSRF-Token"))
        assertTrue(login.getHeader("Cookie").orEmpty().contains("session=anonymous"))
        assertEquals("authenticated", writtenCookies.single { it.name == "session" }.value)
    }

    @Test
    fun `blank account only establishes the anonymous session`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Set-Cookie", "session=anonymous; Path=/")
                .setBody("""{"token":"csrf-token"}"""),
        )

        assertEquals(AuthenticationResult.NoServerLoginRequired, authenticator.authenticate(profile(), ""))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `wrong SillyTavern credentials are reported without exporting a session`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"token":"csrf-token"}"""))
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"Incorrect credentials"}"""))

        assertEquals(AuthenticationResult.InvalidCredentials, authenticator.authenticate(profile("alice"), "wrong"))
        assertTrue(writtenCookies.isEmpty())
    }

    @Test
    fun `basic auth challenge is delegated to WebView`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setHeader("WWW-Authenticate", "Basic realm=server"))

        assertEquals(AuthenticationResult.BasicAuthRequired, authenticator.authenticate(profile("alice"), "secret"))
    }

    @Test
    fun `remembered basic auth is sent on csrf and login requests`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"token":"csrf-token"}"""))
        server.enqueue(MockResponse().setBody("""{"handle":"alice"}"""))

        assertEquals(
            AuthenticationResult.Success,
            authenticator.authenticate(profile("alice"), "secret", "gateway" to "gate-secret"),
        )
        val csrfAuthorization = server.takeRequest().getHeader("Authorization")
        val loginAuthorization = server.takeRequest().getHeader("Authorization")
        assertTrue(csrfAuthorization.orEmpty().startsWith("Basic "))
        assertEquals(csrfAuthorization, loginAuthorization)
    }

    private fun profile(handle: String = "") = StoredProfile(
        baseUrl = server.url("/").toString().trimEnd('/'),
        handle = handle,
        allowPrivateLanHttp = true,
    )
}
