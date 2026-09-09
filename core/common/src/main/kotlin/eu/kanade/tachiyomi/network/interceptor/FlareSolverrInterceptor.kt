package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import android.webkit.CookieManager
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

// SY -->
/** Solves Cloudflare challenges through FlareSolverr; [CloudflareInterceptor] stands down while it is enabled. */
class FlareSolverrInterceptor(
    private val context: Context,
    private val preferences: NetworkPreferences,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalResponse = chain.proceed(originalRequest)

        if (!preferences.enableFlareSolverr.get()) {
            return originalResponse
        }

        if (!(originalResponse.code in ERROR_CODES && originalResponse.header("Server") in SERVER_CHECK)) {
            return originalResponse
        }

        logcat(tag = TAG) { "Cloudflare challenge detected for ${originalRequest.url}" }

        try {
            originalResponse.close()

            val request = runBlocking {
                CFClearance.resolveWithFlareSolverr(originalRequest)
            }

            return chain.proceed(request)
        } catch (e: CloudflareBypassException) {
            throw IOException(context.stringResource(MR.strings.information_cloudflare_bypass_failure), e)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    object CFClearance {
        private val network: NetworkHelper by injectLazy()
        private val json: Json by injectLazy()
        private val preferences: NetworkPreferences by injectLazy()
        private val jsonMediaType = "application/json".toMediaType()
        private val mutex = Mutex()

        /** The shared client minus the Cloudflare interceptors, with timeouts that outlast a full solve. */
        private val client: OkHttpClient by lazy {
            network.client.newBuilder()
                .apply {
                    interceptors().removeAll { it is FlareSolverrInterceptor || it is CloudflareInterceptor }
                }
                .readTimeout(MAX_TIMEOUT_MS + 30_000L, TimeUnit.MILLISECONDS)
                .callTimeout(MAX_TIMEOUT_MS + 60_000L, TimeUnit.MILLISECONDS)
                .build()
        }

        @Serializable
        data class FlareSolverCookie(
            val name: String,
            val value: String,
        )

        @Serializable
        data class FlareSolverRequest(
            val cmd: String,
            val url: String,
            val maxTimeout: Int? = null,
            val session: String? = null,
            @SerialName("session_ttl_minutes")
            val sessionTtlMinutes: Int? = null,
            val cookies: List<FlareSolverCookie>? = null,
            val returnOnlyCookies: Boolean? = null,
            val proxy: String? = null,
            val postData: String? = null,
        )

        /** [expiry] comes from FlareSolverr v3 and [expires] from v2; both are epoch seconds. */
        @Serializable
        data class FlareSolverSolutionCookie(
            val name: String,
            val value: String,
            val domain: String,
            val path: String = "/",
            val expiry: Long? = null,
            val expires: Double? = null,
            val size: Int? = null,
            val httpOnly: Boolean = false,
            val secure: Boolean = false,
            val session: Boolean? = null,
            val sameSite: String? = null,
        )

        @Serializable
        data class FlareSolverSolution(
            val url: String,
            val status: Int,
            val headers: Map<String, String>? = null,
            val response: String? = null,
            val cookies: List<FlareSolverSolutionCookie> = emptyList(),
            val userAgent: String,
        )

        /** [solution] is absent when [status] is `error`; [message] then carries the reason. */
        @Serializable
        data class FlareSolverResponse(
            val status: String,
            val message: String,
            val solution: FlareSolverSolution? = null,
            val startTimestamp: Long? = null,
            val endTimestamp: Long? = null,
            val version: String? = null,
        )

        /** Returns FlareSolverr's answer for [url], keeping its error body instead of throwing on HTTP 500. */
        suspend fun request(
            url: String,
            cookies: List<FlareSolverCookie> = emptyList(),
            maxTimeout: Int = MAX_TIMEOUT_MS,
        ): FlareSolverResponse {
            val body = json.encodeToString(
                FlareSolverRequest(
                    cmd = "request.get",
                    url = url,
                    cookies = cookies.takeIf { it.isNotEmpty() },
                    returnOnlyCookies = true,
                    maxTimeout = maxTimeout,
                ),
            ).toRequestBody(jsonMediaType)

            val response = client.newCall(POST(url = endpoint(), body = body)).await()

            return try {
                with(json) { response.parseAs<FlareSolverResponse>() }
            } catch (e: Exception) {
                if (response.isSuccessful) throw e else throw HttpException(response.code)
            }
        }

        /**
         * Solves the challenge for [originalRequest] and returns it rebuilt with the solver's user agent.
         * Cookies land in [cookieManager], which backs the cookie jar, so OkHttp attaches them itself.
         */
        suspend fun resolveWithFlareSolverr(
            originalRequest: Request,
            cookieManager: CookieManager = CookieManager.getInstance(),
        ): Request {
            logcat(tag = TAG) { "Requesting challenge solution for ${originalRequest.url}" }

            val response = mutex.withLock {
                request(
                    url = originalRequest.url.toString(),
                    cookies = network.cookieJar.get(originalRequest.url)
                        .filterNot { it.name in COOKIE_NAMES }
                        .map { FlareSolverCookie(it.name, it.value) },
                )
            }

            val solution = response.solution
            if (response.status != "ok" || solution == null) {
                logcat(LogPriority.ERROR, tag = TAG) { "Failed to solve challenge: ${response.message}" }
                throw CloudflareBypassException("FlareSolverr: ${response.message}")
            }
            if (solution.cookies.none { it.name in COOKIE_NAMES }) {
                logcat(LogPriority.ERROR, tag = TAG) { "No clearance cookie in solution: ${response.message}" }
                throw CloudflareBypassException("FlareSolverr returned no clearance cookie: ${response.message}")
            }

            logcat(tag = TAG) { "Received challenge solution for ${originalRequest.url}" }

            solution.cookies.forEach { cookie ->
                val domain = cookie.domain.removePrefix(".")
                cookieManager.setCookie("https://$domain", buildCookieString(cookie, domain))
            }

            return originalRequest.newBuilder()
                .header("User-Agent", solution.userAgent)
                .build()
        }

        private fun endpoint(): String {
            val base = preferences.flareSolverrUrl.get().trim().removeSuffix("/")
            return if (base.endsWith("/v1")) base else "$base/v1"
        }

        private fun buildCookieString(cookie: FlareSolverSolutionCookie, domain: String): String {
            return buildString {
                append("${cookie.name}=${cookie.value}; Domain=$domain; Path=${cookie.path}")
                val expiry = cookie.expiry ?: cookie.expires?.toLong()
                if (expiry != null && expiry > 0) {
                    val expires = Instant.ofEpochSecond(expiry)
                        .atZone(ZoneOffset.UTC)
                        .format(DateTimeFormatter.RFC_1123_DATE_TIME)
                    append("; Expires=$expires")
                }
                if (cookie.httpOnly) append("; HttpOnly")
                if (cookie.secure) append("; Secure")
            }
        }
    }
}

private const val TAG = "FlareSolverr"
private const val MAX_TIMEOUT_MS = 60_000
// SY <--
