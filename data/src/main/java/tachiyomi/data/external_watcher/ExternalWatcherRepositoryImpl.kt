package tachiyomi.data.external_watcher

import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.domain.watcher.model.ExternalWatcherRequest
import tachiyomi.domain.watcher.model.ExternalWatcherResponse
import tachiyomi.domain.watcher.repository.ExternalWatcherRepository
import tachiyomi.domain.library.service.LibraryPreferences

class ExternalWatcherRepositoryImpl(
    private val networkHelper: NetworkHelper,
    private val json: Json,
    private val libraryPreferences: LibraryPreferences,
) : ExternalWatcherRepository {

    val host = libraryPreferences.externalWatcherHost().get()

    override suspend fun getExternalWatcher(mangaId: Long, fcmToken: String): Boolean? {
        if (fcmToken.isBlank()) return null
        val response = networkHelper.client.newCall(GET("$host/tracks/comick/$fcmToken")).await()
        if (response.isSuccessful) {
            with(json) {
                val externalWatcherResponse = response.parseAs<List<ExternalWatcherResponse>>()
                return externalWatcherResponse.any { it.mangaId == mangaId }
            }
        }
        return null
    }

    override suspend fun addToExternalWatcher(externalWatcherRequest: ExternalWatcherRequest): Boolean {
        if (externalWatcherRequest.deviceToken.isBlank()) throw Exception("FCM Token is empty!") // unlikely to happen
        val requestBody = Json
            .encodeToString(externalWatcherRequest)
            .toRequestBody("application/json".toMediaType())

        val result = networkHelper.client.newCall(
            POST(
                url = "$host/tracks/comick",
                body = requestBody
            )
        ).await()
        if (!result.isSuccessful) throw ExternalWatcherException(result.message)
        return true
    }

    override suspend fun removeFromExternalWatcher(externalWatcherRequest: ExternalWatcherRequest): Boolean {
        if (externalWatcherRequest.deviceToken.isBlank()) throw Exception("FCM Token is empty!") // unlikely to happen
        val requestBody = Json
            .encodeToString(externalWatcherRequest)
            .toRequestBody("application/json".toMediaType())

        val result = networkHelper.client.newCall(
            DELETE(
                url = "$host/tracks/comick",
                body = requestBody
            )
        ).await()
        if (!result.isSuccessful) throw ExternalWatcherException(result.message)
        return true
    }

    override suspend fun disableExternalWatcher(fcmToken: String): Boolean {
        if (fcmToken.isBlank()) return false

        val result = networkHelper.client.newCall(POST(url = "$host/jobs/delete/$fcmToken")).await()
        if (!result.isSuccessful) throw ExternalWatcherException(result.message)

        return true
    }

    override suspend fun enableExternalWatcher(fcmToken: String, interval: Long): Boolean {
        if (fcmToken.isBlank()) return false
        // Register device token
        val resultRegisterDevice = networkHelper.client.newCall(POST(url = "$host/device/$fcmToken/$interval")).await()
        if (!resultRegisterDevice.isSuccessful) throw ExternalWatcherException(resultRegisterDevice.message)
        // Enable external watcher
        val resultEnable = networkHelper.client.newCall(POST(url = "$host/jobs/create/$fcmToken/$interval")).await()
        if (!resultEnable.isSuccessful) throw ExternalWatcherException(resultEnable.message)

        return true
    }
}

data class ExternalWatcherException(override val message: String?): Exception()
