package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import android.os.Build
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.sync.SyncNotifier
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import logcat.logcat
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.http.HttpStatus
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SyncYomiSyncService(
    context: Context,
    json: Json,
    syncPreferences: SyncPreferences,
    private val notifier: SyncNotifier,

    private val protoBuf: ProtoBuf = Injekt.get(),
) : SyncService(context, json, syncPreferences) {

    private class SyncYomiException(message: String?) : Exception(message)

    private companion object {
        val TIMEOUT = 30.seconds
        val FULL_SYNC_INTERVAL = 24.hours
    }

    @Serializable
    private data class SyncEvent(
        val event: SyncEventStatus,
        @SerialName("device_id")
        val deviceId: String? = null,
        @SerialName("device_name")
        val deviceName: String? = null,
        val message: String? = null,
    )

    @Serializable
    private enum class SyncEventStatus {
        SYNC_STARTED,
        SYNC_SUCCESS,
        SYNC_FAILED,
        SYNC_ERROR,
        SYNC_CANCELLED,
    }

    override suspend fun doSync(syncData: SyncData, full: Boolean): SyncResult {
        reportSyncEvent(SyncEventStatus.SYNC_STARTED)

        var v2 = false
        try {
            v2 = supportsV2()
            return if (v2) {
                doSyncV2(syncData, full)
            } else {
                doSyncV1(syncData)
            }
        } catch (e: Exception) {
            if (e is CancellationException) {
                reportSyncEvent(SyncEventStatus.SYNC_CANCELLED, e.message)
                throw e
            }
            logcat(LogPriority.ERROR) { "Error syncing: ${e.message}" }
            notifier.showSyncError(e.message)
            reportSyncEvent(SyncEventStatus.SYNC_ERROR, e.message)
            return SyncResult(null, changed = false, protocolV2 = v2)
        }
    }

    /**
     * Protocol v1: download, merge locally, upload the merged library.
     */
    private suspend fun doSyncV1(syncData: SyncData): SyncResult {
        val (remoteData, etag) = pullSyncData()

        val finalSyncData = if (remoteData != null) {
            assert(etag.isNotEmpty()) { "ETag should never be empty if remote data is not null" }
            logcat(LogPriority.DEBUG, "SyncService") {
                "Try update remote data with ETag($etag)"
            }
            mergeSyncData(syncData, remoteData)
        } else {
            // init or overwrite remote data
            logcat(LogPriority.DEBUG) {
                "Try overwrite remote data with ETag($etag)"
            }
            syncData
        }

        val success = pushSyncData(finalSyncData, etag)

        if (success) {
            reportSyncEvent(SyncEventStatus.SYNC_SUCCESS)
        } else {
            reportSyncEvent(SyncEventStatus.SYNC_FAILED, "Failed to push sync data")
        }

        return SyncResult(finalSyncData.backup, changed = remoteData != null, protocolV2 = false)
    }

    /**
     * Protocol v2: the server merges. We upload what changed since the last successful sync (or the whole
     * library when [full]) and restore whatever the server says we are missing. No local merge.
     */
    private suspend fun doSyncV2(syncData: SyncData, full: Boolean): SyncResult {
        val backup = syncData.backup ?: return SyncResult(null, changed = false, protocolV2 = true)
        val host = syncPreferences.clientHost.get()
        val apiKey = syncPreferences.clientAPIKey.get()

        val pendingDeleted = syncPreferences.pendingDeletedCategoryUids.get()
        val headersBuilder = baseHeaders(apiKey)
            .add("X-Sync-Cursor", syncPreferences.syncCursor.get().toString())
            .add("X-Sync-Full", full.toString())
        if (pendingDeleted.isNotEmpty()) {
            headersBuilder.add("X-Sync-Deleted-Categories", pendingDeleted.joinToString(","))
        }

        val body = protoBuf.encodeToByteArray(Backup.serializer(), backup)
            .toRequestBody("application/octet-stream".toMediaType())
        val request = POST(
            url = "$host/api/sync/v2/merge",
            headers = headersBuilder.build(),
            body = body,
        )

        val response = syncClient().newCall(request).await()

        if (!response.isSuccessful) {
            val responseBody = response.body.string()
            logcat(LogPriority.ERROR) { "SyncError (${response.code}): $responseBody" }
            reportSyncEvent(SyncEventStatus.SYNC_FAILED, "Server answered ${response.code}")
            throw SyncYomiException("Failed to sync: ${response.code} $responseBody")
        }

        val bytes = response.body.byteStream().use { it.readBytes() }
        val remote = protoBuf.decodeFromByteArray(Backup.serializer(), bytes)
        val cursor = response.headers["X-Sync-Cursor"]?.toLongOrNull()
            ?: throw SyncYomiException("Missing X-Sync-Cursor")
        val changed = response.headers["X-Sync-Changed"]?.toBoolean() ?: true
        val fullRequested = response.headers["X-Sync-Full-Requested"]?.toBoolean() ?: false

        syncPreferences.syncCursor.set(cursor)
        syncPreferences.fullSyncRequested.set(fullRequested)
        // Only drop the uids we actually sent; a category deleted during the request stays pending.
        syncPreferences.pendingDeletedCategoryUids.set(syncPreferences.pendingDeletedCategoryUids.get() - pendingDeleted)
        if (full) {
            syncPreferences.lastFullSync.set(Clock.System.now().toEpochMilliseconds())
        }
        logcat(LogPriority.DEBUG) { "SyncYomi v2 merge done: cursor=$cursor changed=$changed fullRequested=$fullRequested" }

        reportSyncEvent(SyncEventStatus.SYNC_SUCCESS)
        return SyncResult(remote, changed = changed, protocolV2 = true)
    }

    /**
     * A full library is needed on the first sync, when the server asks for it, and once a day as a safety net.
     */
    override suspend fun needsFullSync(): Boolean {
        if (!supportsV2()) return true
        if (syncPreferences.syncCursor.get() == 0L || syncPreferences.fullSyncRequested.get()) return true
        val lastFull = syncPreferences.lastFullSync.get()
        return lastFull == 0L || Clock.System.now() - Instant.fromEpochMilliseconds(lastFull) > FULL_SYNC_INTERVAL
    }

    /**
     * Probes the server once per host for protocol v2 support (`GET /api/sync/v2/capabilities`).
     * 200 means v2, 404 means an old server; anything else is a transient failure and is not cached.
     */
    suspend fun supportsV2(): Boolean {
        val host = syncPreferences.clientHost.get()
        if (syncPreferences.v2ProbedHost.get() == host) {
            return syncPreferences.serverSupportsV2.get()
        }

        val request = GET(
            url = "$host/api/sync/v2/capabilities",
            headers = baseHeaders(syncPreferences.clientAPIKey.get()).build(),
        )
        val response = syncClient().newCall(request).await()
        response.close()
        val supported = when (response.code) {
            HttpStatus.SC_OK -> true
            HttpStatus.SC_NOT_FOUND -> false
            else -> throw SyncYomiException("Failed to probe server capabilities: ${response.code}")
        }
        syncPreferences.v2ProbedHost.set(host)
        syncPreferences.serverSupportsV2.set(supported)
        logcat(LogPriority.INFO) { "SyncYomi server supports protocol v2: $supported" }
        return supported
    }

    private fun baseHeaders(apiKey: String): Headers.Builder = Headers.Builder()
        .add("X-API-Token", apiKey)
        .add("X-Device-ID", syncPreferences.uniqueDeviceID())
        .add("X-Device-Name", Build.MODEL)

    private fun syncClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT)
        .readTimeout(TIMEOUT)
        .writeTimeout(TIMEOUT)
        .build()

    private suspend fun pullSyncData(): Pair<SyncData?, String> {
        val host = syncPreferences.clientHost.get()
        val apiKey = syncPreferences.clientAPIKey.get()
        val downloadUrl = "$host/api/sync/content"

        val headersBuilder = baseHeaders(apiKey)
        val lastETag = syncPreferences.lastSyncEtag.get()
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

        if (response.isSuccessful) {
            val newETag = response.headers["ETag"]
                .takeIf { it?.isNotEmpty() == true } ?: throw SyncYomiException("Missing ETag")

            val byteArray = response.body.byteStream().use {
                return@use it.readBytes()
            }

            return try {
                val backup = protoBuf.decodeFromByteArray(Backup.serializer(), byteArray)
                return Pair(SyncData(backup = backup), newETag)
            } catch (_: SerializationException) {
                logcat(LogPriority.INFO) {
                    "Bad content responsed from server"
                }
                // the body is invalid
                // return default value so we can overwrite it
                Pair(null, "")
            }
        } else {
            val responseBody = response.body.string()
            notifier.showSyncError("Failed to download sync data: $responseBody")
            logcat(LogPriority.ERROR) { "SyncError: $responseBody" }
            throw SyncYomiException("Failed to download sync data: $responseBody")
        }
    }

    /**
     * Return true if update success
     */
    private suspend fun pushSyncData(syncData: SyncData, eTag: String): Boolean {
        val backup = syncData.backup ?: return true

        val host = syncPreferences.clientHost.get()
        val apiKey = syncPreferences.clientAPIKey.get()
        val uploadUrl = "$host/api/sync/content"

        val headersBuilder = baseHeaders(apiKey)
        if (eTag.isNotEmpty()) {
            headersBuilder.add("If-Match", eTag)
        }
        val headers = headersBuilder.build()

        val client = syncClient()

        val byteArray = protoBuf.encodeToByteArray(Backup.serializer(), backup)
        if (byteArray.isEmpty()) {
            throw IllegalStateException(context.stringResource(MR.strings.empty_backup_error))
        }
        val body = byteArray.toRequestBody("application/octet-stream".toMediaType())

        val uploadRequest = PUT(
            url = uploadUrl,
            headers = headers,
            body = body,
        )

        val response = client.newCall(uploadRequest).await()

        if (response.isSuccessful) {
            val newETag = response.headers["ETag"]
                .takeIf { it?.isNotEmpty() == true } ?: throw SyncYomiException("Missing ETag")
            syncPreferences.lastSyncEtag.set(newETag)
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

    private suspend fun reportSyncEvent(event: SyncEventStatus, message: String? = null) {
        withContext(NonCancellable) {
            try {
                val host = syncPreferences.clientHost.get()
                val apiKey = syncPreferences.clientAPIKey.get()
                val url = "$host/api/sync/event"

                val headers = baseHeaders(apiKey).build()

                val bodyObj = SyncEvent(
                    event = event,
                    deviceId = syncPreferences.uniqueDeviceID(),
                    deviceName = Build.MODEL,
                    message = message,
                )

                val jsonBody = json.encodeToString(SyncEvent.serializer(), bodyObj)
                val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = POST(
                    url = url,
                    headers = headers,
                    body = requestBody,
                )

                val client = OkHttpClient()
                client.newCall(request).await().close()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to report sync event: ${e.message}" }
            }
        }
    }
}
