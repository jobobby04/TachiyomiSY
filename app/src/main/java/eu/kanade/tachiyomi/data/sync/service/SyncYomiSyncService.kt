package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.sync.SyncNotifier
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.await
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.http.HttpStatus
import java.util.concurrent.TimeUnit

class SyncYomiSyncService(
    context: Context,
    json: Json,
    syncPreferences: SyncPreferences,
    private val notifier: SyncNotifier,
) : SyncService(context, json, syncPreferences) {

    private class SyncYomiException(message: String?) : Exception(message)

    override suspend fun doSync(syncData: SyncData): Backup? {
        try {
            val (remoteData, etag) = pullSyncData()
            val finalSyncData: SyncData

            if (remoteData != null){
                assert(etag.isNotEmpty()) { "ETag should never be empty if remote data is not null" }
                logcat(LogPriority.DEBUG, "SyncService") {
                    "Try update remote data with ETag($etag)"
                }

                finalSyncData = mergeSyncData(syncData, remoteData)
                pushSyncData(finalSyncData, etag)

            } else {
                // init or overwrite remote data
                logcat(LogPriority.DEBUG) {
                    "Try overwrite remote data with ETag($etag)"
                }

                finalSyncData = syncData

            }

            return if (pushSyncData(finalSyncData, etag)) {
                finalSyncData.backup
            } else {
                // update failed
                null
            }

        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Error syncing: ${e.message}" }
            return null
        }
    }

    private suspend fun pullSyncData(): Pair<SyncData?, String> {
        val host = syncPreferences.clientHost().get()
        val apiKey = syncPreferences.clientAPIKey().get()
        val downloadUrl = "$host/api/sync/content"

        val headersBuilder = Headers.Builder().add("X-API-Token", apiKey)
        val lastETag = syncPreferences.lastSyncEtag().get()
        if (lastETag != "") {
            headersBuilder.add("If-None-Match", lastETag)
        }
        val headers = headersBuilder.build()

        val downloadRequest = GET(
            url = downloadUrl,
            headers = headers,
        )

        val client = OkHttpClient()
        val response = client.newCall(downloadRequest).await()

        if (response.code == HttpStatus.SC_NOT_MODIFIED) {
            // not modified
            assert(lastETag.isNotEmpty())
            logcat(LogPriority.INFO) {
                "Remote server not modified"
            }
            return Pair(null, lastETag)
        } else if (response.code == HttpStatus.SC_NOT_FOUND) {
            // maybe got deleted from remote
            return Pair(null, "")
        }

        val responseBody = response.body.string()

        if (response.isSuccessful) {
            val newETag = response.headers["ETag"] ?: throw SyncYomiException("Missing ETag")
            if (newETag.isEmpty()) {
                throw SyncYomiException("ETag is empty")
            }

            return try {
                Pair(json.decodeFromString(responseBody), newETag)
            } catch (e: SerializationException) {
                logcat(LogPriority.INFO) {
                    "Bad content responsed from server: $responseBody"
                }
                // the body is not a json
                // return default value so we can overwrite it
                Pair(null, "")
            }
        } else {
            notifier.showSyncError("Failed to download sync data: $responseBody")
            logcat(LogPriority.ERROR) { "SyncError:$responseBody" }
            throw SyncYomiException("Failed to download sync data: $responseBody")
        }
    }

    /**
     * Return true if update success
     */
    private suspend fun pushSyncData(syncData: SyncData, eTag: String): Boolean {
        val host = syncPreferences.clientHost().get()
        val apiKey = syncPreferences.clientAPIKey().get()
        val uploadUrl = "$host/api/sync/content"
        val timeout = 30L

        val headersBuilder = Headers.Builder().add("X-API-Token", apiKey)
        if (eTag.isNotEmpty()) {
            headersBuilder.add("If-Match", eTag)
        }
        val headers = headersBuilder.build()

        // Set timeout to 30 seconds
        val client = OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .build()

        val jsonData = json.encodeToString(syncData)
        val body = jsonData.toRequestBody("application/octet-stream".toMediaTypeOrNull())

        val uploadRequest = PUT(
            url = uploadUrl,
            headers = headers,
            body = body,
        )

        val response = client.newCall(uploadRequest).await()

        if (response.isSuccessful) {
            val newETag = response.headers["ETag"] ?: throw SyncYomiException("Missing ETag")
            if (newETag.isEmpty()) {
                throw SyncYomiException("ETag is empty")
            }
            syncPreferences.lastSyncEtag().set(newETag)
            logcat(LogPriority.DEBUG) { "SyncYomi sync completed" }
            return true

        } else if (response.code == HttpStatus.SC_PRECONDITION_FAILED) {
            // other clients updated remote data, will try next time
            logcat(LogPriority.DEBUG) { "SyncYomi sync failed with 412" }
            return false

        } else {
            val responseBody = response.body.string()
            notifier.showSyncError("Failed to upload sync data: $responseBody")
            logcat(LogPriority.ERROR) { "SyncError: $responseBody" }
            return false
        }
    }
}
