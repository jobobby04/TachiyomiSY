package eu.kanade.translation

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class ServerWakeService(
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val translationPreferences: TranslationPreferences = Injekt.get(),
    private val json: Json = Injekt.get(),
) {

    suspend fun prepareServerForQueue() {
        if (!translationPreferences.wakeServerBeforeTranslation().get()) return

        wakeServer()
        waitUntilServerReady()
    }

    suspend fun waitUntilServerReady() {
        val checkUrl = translationPreferences.translationServerCheckUrl().get()
            .trim()
            .ifBlank { translationPreferences.translationServerBaseUrl().get().trim() }
        require(checkUrl.isNotBlank()) { "Server readiness check URL is not configured" }
        require(checkUrl.toHttpUrlOrNull() != null) { "Server readiness check URL is invalid" }

        val retryIntervalMs = translationPreferences.translationServerRetryIntervalSeconds().get() * 1_000L
        val maxWaitMs = translationPreferences.translationServerMaxWaitSeconds().get() * 1_000L
        val startedAt = System.currentTimeMillis()

        while (System.currentTimeMillis() - startedAt <= maxWaitMs) {
            if (isServerReady(checkUrl)) return
            delay(retryIntervalMs.coerceAtLeast(MIN_RETRY_DELAY_MS))
        }

        throw IllegalStateException("MIT server did not become ready within ${maxWaitMs / 1000} seconds")
    }

    private suspend fun wakeServer() {
        when (translationPreferences.wakeMode().get().trim().lowercase()) {
            "webhook" -> triggerWebhook()
            "wol" -> sendWakeOnLan()
            else -> throw IllegalArgumentException("Unsupported wake mode")
        }
    }

    private suspend fun triggerWebhook() = withIOContext {
        val url = translationPreferences.wakeWebhookUrl().get().trim()
        require(url.isNotBlank()) { "Wake webhook URL is not configured" }
        require(url.toHttpUrlOrNull() != null) { "Wake webhook URL is invalid" }

        val method = translationPreferences.wakeWebhookMethod().get().trim().uppercase().ifBlank { "POST" }
        val bodyText = translationPreferences.wakeWebhookBody().get()
        val headersJson = translationPreferences.wakeWebhookHeadersJson().get().ifBlank { "{}" }
        val headers = json.parseToJsonElement(headersJson).jsonObject.mapValues { it.value.jsonPrimitive.content }

        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

        val requestBody = when {
            method == "GET" || method == "HEAD" -> null
            bodyText.isNotBlank() -> bodyText.toRequestBody("application/json; charset=utf-8".toMediaType())
            else -> ByteArray(0).toRequestBody(null, 0, 0)
        }

        val request = requestBuilder.method(method, requestBody).build()

        networkHelper.nonCloudflareClient.newBuilder()
            .callTimeout(WEBHOOK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
            .newCall(request)
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Wake webhook failed with HTTP ${response.code}")
                }
            }
    }

    private suspend fun sendWakeOnLan() = withIOContext {
        val macBytes = parseMacAddress(translationPreferences.wakeMacAddress().get())
        val port = translationPreferences.wakePort().get()
        val address = translationPreferences.wakeBroadcastAddress().get().trim().ifBlank { DEFAULT_BROADCAST }

        val packetBytes = ByteArray(6 + 16 * macBytes.size)
        repeat(6) { packetBytes[it] = 0xFF.toByte() }
        repeat(16) { index ->
            macBytes.copyInto(packetBytes, 6 + index * macBytes.size)
        }

        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.send(DatagramPacket(packetBytes, packetBytes.size, InetAddress.getByName(address), port))
        }
    }

    private suspend fun isServerReady(url: String): Boolean = withIOContext {
        runCatching {
            val request = Request.Builder().url(url).get().build()
            networkHelper.nonCloudflareClient.newBuilder()
                .connectTimeout(READY_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READY_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(READY_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()
                .use { response -> response.code < 500 }
        }.getOrDefault(false)
    }

    private fun parseMacAddress(raw: String): ByteArray {
        val cleaned = raw.trim().replace("-", ":")
        val segments = cleaned.split(":")
        require(segments.size == 6) { "Wake MAC address must contain 6 bytes" }
        return segments.map { segment ->
            require(segment.length == 2) { "Wake MAC address segment '$segment' is invalid" }
            segment.toInt(16).toByte()
        }.toByteArray()
    }

    private companion object {
        const val DEFAULT_BROADCAST = "255.255.255.255"
        const val MIN_RETRY_DELAY_MS = 1_000L
        const val READY_CHECK_TIMEOUT_SECONDS = 5L
        const val WEBHOOK_TIMEOUT_SECONDS = 15L
    }
}
