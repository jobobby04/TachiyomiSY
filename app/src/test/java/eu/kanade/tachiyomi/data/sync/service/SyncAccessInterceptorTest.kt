package eu.kanade.tachiyomi.data.sync.service

import eu.kanade.domain.sync.SyncPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.core.common.preference.InMemoryPreferenceStore

/**
 * SY-specific file
 */
class SyncAccessInterceptorTest {

    private val syncPreferences = SyncPreferences(InMemoryPreferenceStore())
    private val interceptor = SyncAccessInterceptor(syncPreferences)

    private fun proceed(
        url: String = "https://sync.example.com/api/sync/content",
        respond: (Request) -> Response = { response(it) },
    ): Pair<Request, Response> {
        val sent = slot<Request>()
        val chain = mockk<Interceptor.Chain> {
            every { request() } returns Request.Builder().url(url).build()
            every { proceed(capture(sent)) } answers { respond(sent.captured) }
        }
        val response = interceptor.intercept(chain)
        return sent.captured to response
    }

    private fun response(
        request: Request,
        code: Int = 200,
        contentType: String = "application/octet-stream",
        headers: List<Pair<String, String>> = emptyList(),
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("")
        .header("Content-Type", contentType)
        .apply { headers.forEach { (name, value) -> header(name, value) } }
        .body("".toResponseBody())
        .build()

    @Test
    fun `none mode leaves the request untouched`() {
        val (request, _) = proceed()
        assertNull(request.header("Authorization"))
        assertNull(request.header("CF-Access-Client-Id"))
    }

    @Test
    fun `service token mode adds cloudflare headers`() {
        syncPreferences.accessMode.set(SyncAccessMode.CF_SERVICE_TOKEN.value)
        syncPreferences.cfAccessClientId.set("id.access")
        syncPreferences.cfAccessClientSecret.set("secret")

        val (request, _) = proceed()
        assertEquals("id.access", request.header("CF-Access-Client-Id"))
        assertEquals("secret", request.header("CF-Access-Client-Secret"))
    }

    @Test
    fun `custom headers mode adds every header`() {
        syncPreferences.accessMode.set(SyncAccessMode.CUSTOM_HEADERS.value)
        syncPreferences.accessCustomHeaders.set("X-Secret: abc\n\nAuthorization: Bearer token")

        val (request, _) = proceed()
        assertEquals("abc", request.header("X-Secret"))
        assertEquals("Bearer token", request.header("Authorization"))
    }

    @Test
    fun `basic auth mode adds authorization`() {
        syncPreferences.accessMode.set(SyncAccessMode.BASIC_AUTH.value)
        syncPreferences.accessBasicUser.set("user")
        syncPreferences.accessBasicPassword.set("pass")

        val (request, _) = proceed()
        assertEquals("Basic dXNlcjpwYXNz", request.header("Authorization"))
    }

    @Test
    fun `cookie is only sent to its host`() {
        syncPreferences.accessMode.set(SyncAccessMode.BROWSER_COOKIE.value)
        syncPreferences.accessCookie.set("CF_Authorization=jwt")
        syncPreferences.accessCookieHost.set("sync.example.com")

        assertEquals("CF_Authorization=jwt", proceed().first.header("Cookie"))
        assertNull(proceed(url = "https://other.example.com/api/sync/content").first.header("Cookie"))
    }

    @Test
    fun `malformed custom header is rejected`() {
        assertThrows<IllegalArgumentException> { SyncAccessInterceptor.parseCustomHeaders("no colon here") }
    }

    @Test
    fun `stored malformed custom header fails as an io error`() {
        syncPreferences.accessMode.set(SyncAccessMode.CUSTOM_HEADERS.value)
        syncPreferences.accessCustomHeaders.set("no colon here")

        assertThrows<SyncAccessDeniedException> { proceed() }
    }

    @Test
    fun `browser login without a session asks to log in`() {
        syncPreferences.accessMode.set(SyncAccessMode.BROWSER_COOKIE.value)
        val login = { request: Request ->
            response(request, code = 302, headers = listOf("Location" to "/cdn-cgi/access/login/sync"))
        }

        val e = assertThrows<SyncAccessDeniedException> { proceed(respond = login) }
        assertTrue(e.message!!.startsWith("Not logged in"))
    }

    @Test
    fun `redirect to cloudflare access login is denied`() {
        assertThrows<SyncAccessDeniedException> {
            proceed { response(it, code = 302, headers = listOf("Location" to "/cdn-cgi/access/login/sync")) }
        }
    }

    @Test
    fun `cross host redirect is denied only with access protection`() {
        val redirect = { request: Request ->
            response(request, code = 302, headers = listOf("Location" to "https://auth.example.com/"))
        }
        assertEquals(302, proceed(respond = redirect).second.code)

        syncPreferences.accessMode.set(SyncAccessMode.BASIC_AUTH.value)
        assertThrows<SyncAccessDeniedException> { proceed(respond = redirect) }
    }

    @Test
    fun `html pages are denied`() {
        assertThrows<SyncAccessDeniedException> { proceed { response(it, code = 403, contentType = "text/html") } }
        assertThrows<SyncAccessDeniedException> { proceed { response(it, contentType = "text/html; charset=utf-8") } }
    }

    @Test
    fun `sync api errors pass through`() {
        val (_, response) = proceed { response(it, code = 401, contentType = "application/json") }
        assertEquals(401, response.code)
    }
}
