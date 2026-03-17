package eu.kanade.translation

import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class MitApiClient(
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val translationPreferences: TranslationPreferences = Injekt.get(),
) {

    suspend fun translatePage(
        imageBytes: ByteArray,
        fileName: String,
        configJson: String,
    ): ByteArray = withIOContext {
        val preferStream = translationPreferences.translationUseStreamEndpoint().get()
        val endpointUrl = buildEndpointUrl(forceStream = preferStream)
        val timeoutSeconds = translationPreferences.translationRequestTimeoutSeconds().get().toLong()
        val client = networkHelper.nonCloudflareClient.newBuilder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                fileName.ifBlank { DEFAULT_FILE_NAME },
                imageBytes.toRequestBody("application/octet-stream".toMediaType()),
            )
            .addFormDataPart("config", configJson)
            .build()

        val request = Request.Builder()
            .url(endpointUrl)
            .post(requestBody)
            .build()

        val responseBytes = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body.string().take(MAX_ERROR_BODY_LENGTH)
                val suffix = body.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
                throw IllegalStateException("MIT request failed with HTTP ${response.code}$suffix")
            }

            response.body.bytes()
        }

        val decodedBytes = if (preferStream) decodeStreamImage(responseBytes) else responseBytes
        if (!preferStream && isPlaceholderImage(decodedBytes)) {
            retryWithStreamEndpoint(client, requestBody)
        } else {
            decodedBytes
        }
    }

    fun buildEndpointUrl(forceStream: Boolean = translationPreferences.translationUseStreamEndpoint().get()): String {
        val baseUrl = translationPreferences.translationServerBaseUrl().get().trim().trimEnd('/')
        require(baseUrl.isNotBlank()) { "Translation server base URL is not configured" }
        require(baseUrl.toHttpUrlOrNull() != null) { "Translation server base URL is invalid" }

        val endpointPath = translationPreferences.translationEndpointPath().get().trim().ifBlank {
            DEFAULT_ENDPOINT_PATH
        }
        val resolvedPath = if (forceStream) {
            when {
                endpointPath.endsWith("/stream/web") -> endpointPath.removeSuffix("/web")
                endpointPath.endsWith("/stream") -> endpointPath
                else -> endpointPath.trimEnd('/') + "/stream"
            }
        } else {
            endpointPath
                .removeSuffix("/stream/web")
                .removeSuffix("/stream")
        }

        return baseUrl + "/" + resolvedPath.trimStart('/')
    }

    private fun retryWithStreamEndpoint(
        client: okhttp3.OkHttpClient,
        requestBody: MultipartBody,
    ): ByteArray {
        val retryRequest = Request.Builder()
            .url(buildEndpointUrl(forceStream = true))
            .post(requestBody)
            .build()

        return client.newCall(retryRequest).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body.string().take(MAX_ERROR_BODY_LENGTH)
                val suffix = body.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
                throw IllegalStateException("MIT stream retry failed with HTTP ${response.code}$suffix")
            }

            decodeStreamImage(response.body.bytes())
        }
    }

    private companion object {
        const val DEFAULT_ENDPOINT_PATH = "/translate/with-form/image"
        const val DEFAULT_FILE_NAME = "page.png"
        const val MAX_ERROR_BODY_LENGTH = 512
        const val STREAM_STATUS_RESULT: Int = 0
        const val STREAM_STATUS_ERROR: Int = 2
        const val STREAM_HEADER_SIZE = 5
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }

    private fun decodeStreamImage(responseBytes: ByteArray): ByteArray {
        var offset = 0

        while (offset + STREAM_HEADER_SIZE <= responseBytes.size) {
            val status = responseBytes[offset].toInt() and 0xFF
            val payloadSize = (
                ((responseBytes[offset + 1].toInt() and 0xFF) shl 24) or
                    ((responseBytes[offset + 2].toInt() and 0xFF) shl 16) or
                    ((responseBytes[offset + 3].toInt() and 0xFF) shl 8) or
                    (responseBytes[offset + 4].toInt() and 0xFF)
                )
            offset += STREAM_HEADER_SIZE

            require(payloadSize >= 0) { "MIT stream returned a negative payload size" }
            require(offset + payloadSize <= responseBytes.size) { "MIT stream response was truncated" }

            val payload = responseBytes.copyOfRange(offset, offset + payloadSize)
            offset += payloadSize

            when (status) {
                STREAM_STATUS_RESULT -> return payload
                STREAM_STATUS_ERROR -> {
                    val message = payload.toString(StandardCharsets.UTF_8).ifBlank {
                        "MIT stream request failed"
                    }
                    throw IllegalStateException(message)
                }
            }
        }

        throw IllegalStateException("MIT stream response did not contain a result frame")
    }

    private fun isPlaceholderImage(imageBytes: ByteArray): Boolean {
        if (imageBytes.size < 24) return false
        if (!imageBytes.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)) return false
        if (!imageBytes.copyOfRange(12, 16).contentEquals("IHDR".toByteArray(StandardCharsets.US_ASCII))) return false

        val width = readInt(imageBytes, 16)
        val height = readInt(imageBytes, 20)
        return width == 1 && height == 1
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int {
        return (
            ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
            )
    }
}
