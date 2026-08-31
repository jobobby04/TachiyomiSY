package eu.kanade.tachiyomi.data.storage.gdrive

import android.util.Base64
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import tachiyomi.core.common.util.system.logcat
import java.io.IOException
import java.net.ServerSocket
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * OAuth 2.0 for installed apps, loopback variant.
 *
 * The redirect lands on a short-lived local socket, so nothing has to be baked into
 * the manifest and the user can swap credentials without a rebuild. The listener
 * binds every interface — a loopback-only bind is refused on some devices — and
 * rejects any caller that is not on the loopback address.
 */
class GoogleDriveAuth(
    private val client: OkHttpClient,
    private val preferences: GoogleDrivePreferences,
) {
    private val json = Json { ignoreUnknownKeys = true }

    class Session(
        val authorizationUrl: String,
        internal val verifier: String,
        internal val server: ServerSocket,
    ) {
        val redirectUri: String get() = "http://127.0.0.1:${server.localPort}"
    }

    fun getEffectiveClientId(): String {
        val custom = preferences.clientId.get().trim()
        return custom.ifBlank { eu.kanade.tachiyomi.BuildConfig.GDRIVE_CLIENT_ID.ifBlank { DEFAULT_CLIENT_ID } }
    }

    fun getEffectiveClientSecret(): String {
        val custom = preferences.clientSecret.get().trim()
        return custom.ifBlank { eu.kanade.tachiyomi.BuildConfig.GDRIVE_CLIENT_SECRET.ifBlank { DEFAULT_CLIENT_SECRET } }
    }

    /** Opens the loopback socket and builds the URL the user must visit. */
    fun beginSession(): Session {
        val clientId = getEffectiveClientId()
        require(clientId.isNotBlank()) { "Google Drive client id is not configured" }

        val verifier = randomUrlSafe(64)
        val challenge = base64Url(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
        )
        val server = ServerSocket(0, 50, null).apply {
            soTimeout = REDIRECT_TIMEOUT_MS
        }
        val redirect = "http://127.0.0.1:${server.localPort}"

        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("accounts.google.com")
            .addPathSegments("o/oauth2/v2/auth")
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("redirect_uri", redirect)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", SCOPE)
            .addQueryParameter("code_challenge", challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("access_type", "offline")
            .addQueryParameter("prompt", "consent")
            .build()
            .toString()

        return Session(url, verifier, server)
    }

    /**
     * Blocks on the local socket until Google redirects back, then trades the
     * authorization code for tokens. Always closes the socket.
     */
    fun awaitToken(session: Session): GoogleDriveOAuth {
        val deadline = System.currentTimeMillis() + REDIRECT_TIMEOUT_MS
        var authCode: String? = null
        var oauthError: String? = null

        session.server.use { server ->
            while (authCode == null && oauthError == null && System.currentTimeMillis() < deadline) {
                try {
                    val remaining = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(1000)
                    server.soTimeout = remaining
                    server.accept().use { socket ->
                        // The listener binds every interface because a loopback-only
                        // bind is refused on some devices, so drop anything that did
                        // not originate on this device before reading a byte of it.
                        if (!socket.inetAddress.isLoopbackAddress) {
                            logcat(LogPriority.WARN) { "Drive auth: rejected non-local caller" }
                            return@use
                        }
                        val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                        val requestLine = reader.readLine().orEmpty()
                        // Only the request target: the query string carries the
                        // authorization code and must never reach the log.
                        logcat(LogPriority.INFO) {
                            "Drive auth callback: ${requestLine.substringBefore('?').trim()}"
                        }

                        if (requestLine.startsWith("OPTIONS", ignoreCase = true) ||
                            requestLine.contains("/probe", ignoreCase = true)
                        ) {
                            socket.getOutputStream().apply {
                                write(PREFLIGHT_RESPONSE)
                                flush()
                            }
                        } else {
                            val code = extractCode(requestLine)
                            val error = extractError(requestLine)
                            if (code != null) {
                                authCode = code
                                socket.getOutputStream().apply {
                                    write(buildResponse(SUCCESS_PAGE))
                                    flush()
                                }
                            } else if (error != null) {
                                oauthError = error
                                socket.getOutputStream().apply {
                                    write(buildResponse(errorPage(error)))
                                    flush()
                                }
                            } else {
                                // Probe or generic GET without code (e.g. favicon)
                                socket.getOutputStream().apply {
                                    write(buildResponse(SUCCESS_PAGE))
                                    flush()
                                }
                            }
                        }
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    break
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "gdrive: socket exception while awaiting token" }
                }
            }
        }

        oauthError?.let {
            throw IOException("Google authorization failed: $it")
        }

        val finalCode = authCode
            ?: throw IOException("Google did not return an authorization code (timeout or connection lost)")

        return exchange(
            FormBody.Builder()
                .add("code", finalCode)
                .add("client_id", getEffectiveClientId())
                .add("client_secret", getEffectiveClientSecret())
                .add("redirect_uri", session.redirectUri)
                .add("grant_type", "authorization_code")
                .add("code_verifier", session.verifier)
                .build(),
        )
    }

    fun refresh(current: GoogleDriveOAuth): GoogleDriveOAuth {
        val refreshToken = current.refreshToken
            ?: throw IOException("Google Drive: no refresh token stored, sign in again")
        return exchange(
            FormBody.Builder()
                .add("refresh_token", refreshToken)
                .add("client_id", getEffectiveClientId())
                .add("client_secret", getEffectiveClientSecret())
                .add("grant_type", "refresh_token")
                .build(),
            refreshTokenFallback = refreshToken,
        )
    }

    private fun exchange(form: FormBody, refreshTokenFallback: String? = null): GoogleDriveOAuth {
        val request = Request.Builder().url(TOKEN_URL).post(form).build()
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                throw IOException("Google token endpoint ${response.code}: ${text.take(300)}")
            }
            return json.decodeFromString<GoogleDriveOAuth>(text).stamped(refreshTokenFallback)
        }
    }

    private fun extractCode(requestLine: String): String? {
        // "GET /?code=4/0Ax...&scope=... HTTP/1.1"
        val target = requestLine.split(' ').getOrNull(1) ?: return null
        return target.substringAfter('?', "")
            .split('&')
            .map { it.split('=', limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == "code" }
            ?.get(1)
    }

    private fun extractError(requestLine: String): String? {
        // "GET /?error=access_denied HTTP/1.1"
        val target = requestLine.split(' ').getOrNull(1) ?: return null
        return target.substringAfter('?', "")
            .split('&')
            .map { it.split('=', limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == "error" }
            ?.get(1)
    }

    private fun randomUrlSafe(bytes: Int): String {
        val random = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return base64Url(random)
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    companion object {
        const val DEFAULT_CLIENT_ID = ""
        const val DEFAULT_CLIENT_SECRET = ""

        /** Only files this app creates. Non-sensitive, so no Google review is needed. */
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val REDIRECT_TIMEOUT_MS = 5 * 60 * 1000

        private fun buildResponse(body: String, contentType: String = "text/html; charset=utf-8"): ByteArray {
            val bodyBytes = body.toByteArray(Charsets.UTF_8)
            val headers = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: ").append(contentType).append("\r\n")
                append("Content-Length: ").append(bodyBytes.size).append("\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Access-Control-Allow-Private-Network: true\r\n")
                append("Access-Control-Allow-Methods: GET, POST, OPTIONS, HEAD\r\n")
                append("Access-Control-Allow-Headers: *\r\n")
                append("Connection: close\r\n\r\n")
            }
            return headers.toByteArray(Charsets.UTF_8) + bodyBytes
        }

        private val PREFLIGHT_RESPONSE: ByteArray = buildResponse("")

        private val SUCCESS_PAGE = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>TachiyomiSY</title></head>
            <body style="font-family:system-ui,-apple-system,sans-serif;text-align:center;padding:4em 1em;background:#121212;color:#ffffff;">
              <h2 style="color:#4CAF50;margin-bottom:0.5em;">Signed in</h2>
              <p style="color:#cccccc;font-size:1.1em;">You can close this tab and go back to the app.</p>
            </body>
            </html>
        """.trimIndent()

        private fun errorPage(error: String) = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>TachiyomiSY</title></head>
            <body style="font-family:system-ui,-apple-system,sans-serif;text-align:center;padding:4em 1em;background:#121212;color:#ffffff;">
              <h2 style="color:#F44336;margin-bottom:0.5em;">Sign-in failed</h2>
              <p style="color:#cccccc;font-size:1.1em;">$error</p>
              <p style="color:#999999;">Go back to the app and try again.</p>
            </body>
            </html>
        """.trimIndent()
    }
}
