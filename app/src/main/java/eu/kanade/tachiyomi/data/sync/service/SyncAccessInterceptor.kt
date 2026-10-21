package eu.kanade.tachiyomi.data.sync.service

import eu.kanade.domain.sync.SyncPreferences
import okhttp3.Credentials
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.apache.http.HttpStatus
import java.io.IOException

enum class SyncAccessMode(val value: Int) {
    NONE(0),
    CF_SERVICE_TOKEN(1),
    CUSTOM_HEADERS(2),
    BASIC_AUTH(3),
    BROWSER_COOKIE(4),
    ;

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: NONE
    }
}

class SyncAccessDeniedException(message: String) : IOException(message)

/**
 * Adds the access proxy credentials (Cloudflare Access, basic auth, SSO cookie...) and turns a proxy login page or
 * denial into a [SyncAccessDeniedException]. Install it as a network interceptor so every redirect hop is checked.
 */
class SyncAccessInterceptor(
    private val syncPreferences: SyncPreferences,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val mode = SyncAccessMode.fromInt(syncPreferences.accessMode.get())
        val request = chain.request().withAccess(mode)
        val response = chain.proceed(request)

        val reason = blockReason(response, mode) ?: return response
        response.close()
        throw SyncAccessDeniedException(deniedMessage(mode, reason))
    }

    private fun Request.withAccess(mode: SyncAccessMode): Request {
        val builder = newBuilder()
        when (mode) {
            SyncAccessMode.NONE -> return this
            SyncAccessMode.CF_SERVICE_TOKEN ->
                builder
                    .header("CF-Access-Client-Id", syncPreferences.cfAccessClientId.get().trim())
                    .header("CF-Access-Client-Secret", syncPreferences.cfAccessClientSecret.get().trim())
            SyncAccessMode.CUSTOM_HEADERS -> parseCustomHeaders(syncPreferences.accessCustomHeaders.get())
                .forEach { (name, value) -> builder.header(name, value) }
            SyncAccessMode.BASIC_AUTH -> builder.header(
                "Authorization",
                Credentials.basic(syncPreferences.accessBasicUser.get(), syncPreferences.accessBasicPassword.get()),
            )
            SyncAccessMode.BROWSER_COOKIE -> {
                val cookie = syncPreferences.accessCookie.get()
                if (cookie.isNotBlank() && url.host.equals(syncPreferences.accessCookieHost.get(), ignoreCase = true)) {
                    builder.header("Cookie", cookie)
                }
            }
        }
        return builder.build()
    }

    private fun blockReason(response: Response, mode: SyncAccessMode): String? {
        val url = response.request.url

        if (response.isRedirect) {
            val location = response.header("Location")?.let { url.resolve(it) } ?: return null
            return when {
                location.isAccessLogin() -> "redirected to ${location.host}"
                mode != SyncAccessMode.NONE && !location.host.equals(url.host, ignoreCase = true) ->
                    "redirected to ${location.host}"
                else -> null
            }
        }

        if (response.header("cf-mitigated").equals("challenge", ignoreCase = true)) {
            return "Cloudflare challenge"
        }

        // The SyncYomi API never answers with HTML, so an HTML page is a proxy login or error page.
        val isHtml = response.header("Content-Type")?.startsWith("text/html", ignoreCase = true) == true
        val isDenied = response.code == HttpStatus.SC_UNAUTHORIZED || response.code == HttpStatus.SC_FORBIDDEN
        val fromProxy = response.headers.names().any { it.startsWith("cf-access", ignoreCase = true) } ||
            (response.code == HttpStatus.SC_UNAUTHORIZED && response.header("WWW-Authenticate") != null)
        return when {
            isDenied && (isHtml || fromProxy) -> "HTTP ${response.code}"
            isHtml && response.isSuccessful -> "got a web page instead of the sync API"
            else -> null
        }
    }

    private fun HttpUrl.isAccessLogin(): Boolean =
        encodedPath.startsWith("/cdn-cgi/access") || host.endsWith(".cloudflareaccess.com", ignoreCase = true)

    private fun deniedMessage(mode: SyncAccessMode, reason: String): String = when (mode) {
        SyncAccessMode.NONE ->
            "The sync server is behind an access proxy ($reason). Set up access protection in the sync settings."
        SyncAccessMode.CF_SERVICE_TOKEN ->
            "Cloudflare Access rejected the service token ($reason). Check the client ID, secret and policy."
        SyncAccessMode.BROWSER_COOKIE ->
            "The access proxy session expired ($reason). Log in again from the sync settings."
        SyncAccessMode.CUSTOM_HEADERS, SyncAccessMode.BASIC_AUTH ->
            "Blocked by the access proxy ($reason). Check the access protection credentials."
    }

    companion object {
        /** Parses one `Name: value` header per line, throws [IllegalArgumentException] on a malformed line. */
        fun parseCustomHeaders(text: String): Headers {
            val builder = Headers.Builder()
            text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { line ->
                    val index = line.indexOf(':')
                    require(index > 0) { "Expected \"Name: value\": $line" }
                    builder.add(line.substring(0, index).trim(), line.substring(index + 1).trim())
                }
            return builder.build()
        }
    }
}
