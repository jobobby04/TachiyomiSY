package eu.kanade.tachiyomi.data.storage.gdrive

import android.os.StrictMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InputStream

/**
 * Blocking wrapper over the Drive v3 REST API.
 *
 * Blocking on purpose: every caller comes in through [com.hippo.unifile.UniFile],
 * whose contract is synchronous, and those calls already run on IO dispatchers.
 */
class GoogleDriveApi(
    private val client: OkHttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private inline fun <T> allowNetwork(block: () -> T): T {
        val oldPolicy = StrictMode.getThreadPolicy()
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().permitAll().build())
        return try {
            block()
        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
        }
    }

    private fun body(response: okhttp3.Response): String {
        response.use {
            val text = it.body.string()
            if (!it.isSuccessful) {
                throw IOException("Drive API ${it.code}: ${text.take(300)}")
            }
            return text
        }
    }

    /** Lists every non-trashed child of [parentId], following pagination. */
    fun listChildren(parentId: String): List<DriveEntry> = allowNetwork {
        val out = mutableListOf<DriveEntry>()
        var pageToken: String? = null
        do {
            val url = FILES_URL.toHttpUrl().newBuilder()
                .addQueryParameter("q", "'$parentId' in parents and trashed = false")
                .addQueryParameter("fields", "nextPageToken, files($ENTRY_FIELDS)")
                .addQueryParameter("pageSize", "1000")
                .addQueryParameter("supportsAllDrives", "true")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val page = json.decodeFromString<DriveFileList>(
                body(client.newCall(Request.Builder().url(url).build()).execute()),
            )
            out += page.files
            pageToken = page.nextPageToken
        } while (pageToken != null)
        out
    }

    fun getEntry(fileId: String): DriveEntry? = allowNetwork {
        try {
            val url = "$FILES_URL/$fileId".toHttpUrl().newBuilder()
                .addQueryParameter("fields", ENTRY_FIELDS)
                .build()
            json.decodeFromString<DriveEntry>(
                body(client.newCall(Request.Builder().url(url).build()).execute()),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun createFolder(parentId: String, name: String): DriveEntry =
        createMetadata(parentId, name, DriveEntry.FOLDER_MIME)

    fun createFile(parentId: String, name: String, mimeType: String): DriveEntry =
        createMetadata(parentId, name, mimeType)

    private fun createMetadata(parentId: String, name: String, mimeType: String): DriveEntry = allowNetwork {
        val payload = buildString {
            append("{\"name\":").append(quote(name))
            append(",\"mimeType\":").append(quote(mimeType))
            append(",\"parents\":[").append(quote(parentId)).append("]}")
        }
        val url = FILES_URL.toHttpUrl().newBuilder()
            .addQueryParameter("fields", ENTRY_FIELDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(JSON_MIME))
            .build()
        json.decodeFromString(body(client.newCall(request).execute()))
    }

    fun rename(fileId: String, newName: String): Boolean = allowNetwork {
        try {
            val request = Request.Builder()
                .url("$FILES_URL/$fileId")
                .patch("{\"name\":${quote(newName)}}".toRequestBody(JSON_MIME))
                .build()
            body(client.newCall(request).execute())
            true
        } catch (_: Exception) {
            false
        }
    }

    fun delete(fileId: String): Boolean = allowNetwork {
        try {
            val request = Request.Builder().url("$FILES_URL/$fileId").delete().build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /** Caller owns the returned stream and must close it. */
    fun openDownloadStream(fileId: String): InputStream = allowNetwork {
        val url = "$FILES_URL/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "media")
            .build()
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw IOException("Drive download failed: $code")
        }
        response.body.byteStream()
    }

    /** Replaces the whole content of an existing Drive file. */
    fun uploadContent(fileId: String, requestBody: RequestBody) = allowNetwork {
        val url = "$UPLOAD_URL/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "media")
            .build()
        body(
            client.newCall(Request.Builder().url(url).patch(requestBody).build()).execute(),
        )
    }

    /** Fetches the user's storage quota and account details. */
    fun getAbout(): DriveAbout? = allowNetwork {
        try {
            val url = ABOUT_URL.toHttpUrl().newBuilder()
                .addQueryParameter("fields", "storageQuota,user")
                .build()
            json.decodeFromString<DriveAbout>(
                body(client.newCall(Request.Builder().url(url).build()).execute()),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun quote(value: String): String = JsonPrimitive(value).toString()

    companion object {
        private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private const val ABOUT_URL = "https://www.googleapis.com/drive/v3/about"
        private const val ENTRY_FIELDS = "id,name,mimeType,size,modifiedTime,trashed,parents"
        private val JSON_MIME = "application/json; charset=utf-8".toMediaType()
    }
}
