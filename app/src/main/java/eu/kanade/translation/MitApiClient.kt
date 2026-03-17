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
        val endpointUrl = buildEndpointUrl()
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

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body.string().take(MAX_ERROR_BODY_LENGTH)
                val suffix = body.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
                throw IllegalStateException("MIT request failed with HTTP ${response.code}$suffix")
            }

            val responseBytes = response.body.bytes()
            if (translationPreferences.translationUseStreamEndpoint().get()) {
                decodeStreamImage(responseBytes)
            } else {
                responseBytes
            }
        }
    }

    fun buildEndpointUrl(): String {
        val baseUrl = translationPreferences.translationServerBaseUrl().get().trim().trimEnd('/')
        require(baseUrl.isNotBlank()) { "Translation server base URL is not configured" }
        require(baseUrl.toHttpUrlOrNull() != null) { "Translation server base URL is invalid" }

        val endpointPath = translationPreferences.translationEndpointPath().get().trim().ifBlank {
            DEFAULT_ENDPOINT_PATH
        }
        val resolvedPath = if (translationPreferences.translationUseStreamEndpoint().get()) {
            when {
                endpointPath.endsWith("/stream") || endpointPath.endsWith("/stream/web") -> endpointPath
                else -> endpointPath.trimEnd('/') + "/stream"
            }
        } else {
            endpointPath.removeSuffix("/stream")
        }

        return baseUrl + "/" + resolvedPath.trimStart('/')
    }

    private companion object {
        const val DEFAULT_ENDPOINT_PATH = "/translate/with-form/image"
        const val DEFAULT_FILE_NAME = "page.png"
        const val MAX_ERROR_BODY_LENGTH = 512
        const val STREAM_STATUS_RESULT: Int = 0
        const val STREAM_STATUS_ERROR: Int = 2
        const val STREAM_HEADER_SIZE = 5
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
}
