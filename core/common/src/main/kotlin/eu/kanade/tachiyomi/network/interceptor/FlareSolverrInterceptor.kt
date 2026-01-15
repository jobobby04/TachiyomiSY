package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.widget.Toast
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
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
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class FlareSolverrInterceptor(
    private val context: Context,
    private val preferences: NetworkPreferences,
) : Interceptor {
    /**
     * Intercepts an OkHttp request and, when a Cloudflare anti-bot challenge is detected, attempts to
     * solve it via FlareSolverr and retry the request; otherwise returns the original response.
     *
     * @return The response to use for the request: the original response if no challenge was detected
     * or the response from the retried request after a successful FlareSolverr solve.
     * @throws IOException If solving the Cloudflare challenge fails; the original error is wrapped.
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val originalResponse = chain.proceed(originalRequest)

        // Check if Cloudflare anti-bot is on
        if (!(originalResponse.code in ERROR_CODES && originalResponse.header("Server") in SERVER_CHECK)) {
            return originalResponse
        }

        // FlareSolverr is disabled, so just return the original response.
        if (!preferences.enableFlareSolverr().get()) {
            return originalResponse
        }

        logcat(LogPriority.INFO) { "🔄 FlareSolverr: Cloudflare challenge detected at ${originalRequest.url.host}" }

        // Show toast notification that FlareSolverr is working
        if (preferences.showFlareSolverrNotifications().get()) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    context.stringResource(MR.strings.flare_solverr_solving_challenge),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

        return try {
            originalResponse.close()

            val startTime = System.currentTimeMillis()
            val request =
                runBlocking {
                    CFClearance.resolveWithFlareSolverr(originalRequest)
                }
            val duration = System.currentTimeMillis() - startTime

            logcat(LogPriority.INFO) { "✅ FlareSolverr: Challenge solved in ${duration}ms for ${originalRequest.url.host}" }

            // Show success notification
            if (preferences.showFlareSolverrNotifications().get()) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        context.stringResource(MR.strings.flare_solverr_challenge_solved),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }

            chain.proceed(request)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "❌ FlareSolverr: Failed to solve challenge for ${originalRequest.url.host}" }

            // Show error notification
            if (preferences.showFlareSolverrNotifications().get()) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        context.stringResource(MR.strings.flare_solverr_challenge_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }

            // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
            // we don't crash the entire app
            throw IOException(e)
        }
    }

    object CFClearance {
        private val network: NetworkHelper by injectLazy()
        private val json: Json by injectLazy()
        private val jsonMediaType = "application/json".toMediaType()
        private val networkPreferences: NetworkPreferences by injectLazy()
        private val mutex = Mutex()

        private val flareSolverrClient by lazy {
            network.client.newBuilder()
                .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
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
            val session: List<String>? = null,
            @SerialName("session_ttl_minutes")
            val sessionTtlMinutes: Int? = null,
            val cookies: List<FlareSolverCookie>? = null,
            val returnOnlyCookies: Boolean? = null,
            val proxy: String? = null,
            val postData: String? = null, // only used with cmd 'request.post'
        )

        @Serializable
        data class FlareSolverSolutionCookie(
            val name: String,
            val value: String,
            val domain: String,
            val path: String,
            val expires: Double? = null,
            val size: Int? = null,
            val httpOnly: Boolean,
            val secure: Boolean,
            val session: Boolean? = null,
            val sameSite: String,
        )

        @Serializable
        data class FlareSolverSolution(
            val url: String,
            val status: Int,
            val headers: Map<String, String>? = null,
            val response: String? = null,
            val cookies: List<FlareSolverSolutionCookie>,
            val userAgent: String,
        )

        @Serializable
        data class FlareSolverResponse(
            val solution: FlareSolverSolution,
            val status: String,
            val message: String,
            val startTimestamp: Long,
            val endTimestamp: Long,
            val version: String,
        )

        /**
         * Obtains a Cloudflare challenge solution from a FlareSolverr service and returns a new request
         * for the original URL with the solver-provided cookies and User-Agent applied.
         *
         * Contacts the configured FlareSolverr v1 endpoint, requests a solution for the given request URL,
         * installs returned cookies into the provided CookieManager, and builds a new Request with a
         * combined `Cookie` header and the solver `User-Agent`.
         *
         * @param originalRequest The original OkHttp Request that triggered the Cloudflare challenge.
         * @param cookieManager The CookieManager to install cookies into; defaults to CookieManager.getInstance().
         * @return A new Request identical to `originalRequest` but with `Cookie` and `User-Agent` headers set
         *         according to the FlareSolverr solution.
         * @throws IllegalArgumentException If the FlareSolverr URL is not configured.
         * @throws CloudflareBypassException If FlareSolverr returns a non-success response for the challenge.
         */
        suspend fun resolveWithFlareSolverr(
            originalRequest: Request,
            cookieManager: CookieManager = CookieManager.getInstance(),
        ): Request {
            var flareSolverrUrl = networkPreferences.flareSolverrUrl().get().trim()
            require(flareSolverrUrl.isNotBlank()) { "FlareSolverr URL is not configured" }

            // Ensure URL ends with /v1 (FlareSolverr API endpoint)
            if (!flareSolverrUrl.endsWith("/v1")) {
                flareSolverrUrl = flareSolverrUrl.trimEnd('/') + "/v1"
            }

            logcat(LogPriority.DEBUG) { "Requesting challenge solution for ${originalRequest.url}" }

            val flareSolverResponse =
                with(json) {
                    mutex.withLock {
                        flareSolverrClient.newCall(
                            POST(
                                url = flareSolverrUrl,
                                body =
                                Json.encodeToString(
                                    FlareSolverRequest(
                                        "request.get",
                                        originalRequest.url.toString(),
                                        cookies =
                                        network.cookieJar.get(originalRequest.url).map {
                                            FlareSolverCookie(it.name, it.value)
                                        },
                                        returnOnlyCookies = true,
                                        maxTimeout = 90000,
                                    ),
                                ).toRequestBody(jsonMediaType),
                            ),
                        ).awaitSuccess().parseAs<FlareSolverResponse>()
                    }
                }

            if (flareSolverResponse.solution.status in 200..299) {
                logcat(LogPriority.DEBUG) { "Received challenge solution for ${originalRequest.url}" }
                logcat(LogPriority.DEBUG) { "Received ${flareSolverResponse.solution.cookies.size} cookies from FlareSolverr" }

                flareSolverResponse.solution.cookies.forEach { cookie ->
                    try {
                        val domain = cookie.domain.removePrefix(".")
                        val cookieString = buildCookieString(cookie, domain)
                        cookieManager.setCookie("https://$domain", cookieString)
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Error creating cookie for ${cookie.name}" }
                        throw e
                    }
                }

                // Verify if the cookies are set correctly
                val allCookies = flareSolverResponse.solution.cookies.mapNotNull { cookie ->
                    val domain = cookie.domain.removePrefix(".")
                    cookieManager.getCookie("https://$domain")
                }.joinToString("; ")

                return originalRequest.newBuilder()
                    .header("Cookie", allCookies)
                    .header("User-Agent", flareSolverResponse.solution.userAgent)
                    .build()
            } else {
                logcat(LogPriority.ERROR) { "Failed to solve challenge: ${flareSolverResponse.message}" }
                throw CloudflareBypassException("Failed to solve challenge: ${flareSolverResponse.message}")
            }
        }

        /**
         * Constructs an HTTP cookie header string for the given FlareSolverr solution cookie and domain.
         *
         * Formats the `Expires` attribute as an RFC 1123 UTC date when `cookie.expires` is present and greater than zero;
         * otherwise uses "Fri, 31 Dec 9999 23:59:59 GMT" to indicate a distant-future expiry. Includes `Domain`, `Path`,
         * and appends `HttpOnly` and `Secure` attributes when applicable.
         *
         * @param cookie The FlareSolverr solution cookie containing `name`, `value`, `path`, `expires`, `httpOnly`, and `secure`.
         * @param domain The domain value to use for the cookie's `Domain` attribute.
         * @return A formatted cookie header string suitable for use in an HTTP `Cookie` header.
         **/
        private fun buildCookieString(cookie: FlareSolverSolutionCookie, domain: String): String {
            val formatter = DateTimeFormatter.RFC_1123_DATE_TIME
            val expires = if (cookie.expires != null && cookie.expires > 0) {
                // cookie.expires is a Unix epoch timestamp (seconds since Jan 1, 1970)
                ZonedDateTime.ofInstant(
                    Instant.ofEpochSecond(cookie.expires.toLong()),
                    java.time.ZoneOffset.UTC,
                ).format(formatter)
            } else {
                "Fri, 31 Dec 9999 23:59:59 GMT"
            }

            return StringBuilder().apply {
                append("${cookie.name}=${cookie.value}; Domain=$domain; Path=${cookie.path}; Expires=$expires;")
                if (cookie.httpOnly) append(" HttpOnly;")
                if (cookie.secure) append(" Secure;")
            }.toString()
        }
    }
}
