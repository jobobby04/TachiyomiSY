package com.hippo.unifile

import android.app.Application
import android.net.Uri
import android.webkit.MimeTypeMap
import eu.kanade.tachiyomi.data.storage.gdrive.DriveEntry
import eu.kanade.tachiyomi.data.storage.gdrive.GoogleDriveService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * A [UniFile] backed by Google Drive.
 *
 * Declared in `com.hippo.unifile` because UniFile's constructor is package-private;
 * that is the only reason this file sits outside the app's own package tree.
 *
 * Directory listings are cached briefly: the download bookkeeping walks the whole
 * tree and would otherwise issue thousands of round trips per scan.
 */
class DriveFile private constructor(
    parent: UniFile?,
    private var entry: DriveEntry,
) : UniFile(parent) {

    private val service: GoogleDriveService by lazy { Injekt.get() }

    val fileId: String get() = entry.id

    override fun getUri(): Uri = Uri.parse(GoogleDriveService.URI_SCHEME + "://" + entry.id)

    override fun getName(): String? = entry.name

    override fun getType(): String? = if (entry.isFolder) null else entry.mimeType

    /** Drive has no filesystem path; callers fall back to the URI. */
    override fun getFilePath(): String? = null

    override fun isDirectory(): Boolean = entry.isFolder

    override fun isFile(): Boolean = !entry.isFolder

    override fun lastModified(): Long = entry.modifiedTime?.let(::parseRfc3339) ?: 0L

    override fun length(): Long = if (entry.isFolder) 0L else entry.lengthBytes

    override fun canRead(): Boolean = true

    override fun canWrite(): Boolean = true

    /**
     * A handle straight from [fromUri] carries no metadata yet. If the lookup cannot
     * run — no network, or a main-thread caller — such a handle reports itself present
     * rather than making the storage root look broken; the next real read decides.
     */
    override fun exists(): Boolean = runCatching {
        val fresh = service.api.getEntry(entry.id)
        if (fresh != null) entry = fresh
        fresh != null && !fresh.trashed
    }.getOrDefault(entry.name == null)

    override fun listFiles(): Array<UniFile>? = runCatching {
        childEntries().map { of(this, it) as UniFile }.toTypedArray()
    }.getOrNull()

    override fun listFiles(filter: FilenameFilter?): Array<UniFile>? {
        val all = listFiles() ?: return null
        if (filter == null) return all
        return all.filter { filter.accept(this, it.name.orEmpty()) }.toTypedArray()
    }

    override fun findFile(displayName: String?): UniFile? {
        if (displayName.isNullOrBlank()) return null
        return childEntries()
            .firstOrNull { it.name == displayName }
            ?.let { of(this, it) }
    }

    override fun createFile(displayName: String?): UniFile? {
        if (displayName.isNullOrBlank()) return null
        findFile(displayName)?.let { return if (it.isFile) it else null }
        return runCatching {
            val created = service.api.createFile(entry.id, displayName, mimeOf(displayName))
            DriveCache.invalidate(entry.id)
            of(this, created)
        }.getOrNull()
    }

    override fun createDirectory(displayName: String?): UniFile? {
        if (displayName.isNullOrBlank()) return null
        findFile(displayName)?.let { return if (it.isDirectory) it else null }
        return runCatching {
            val created = service.api.createFolder(entry.id, displayName)
            DriveCache.invalidate(entry.id)
            of(this, created)
        }.getOrNull()
    }

    override fun renameTo(displayName: String?): Boolean {
        if (displayName.isNullOrBlank()) return false
        if (!service.api.rename(entry.id, displayName)) return false
        entry = entry.copy(name = displayName)
        invalidateParents()
        return true
    }

    override fun delete(): Boolean {
        val deleted = service.api.delete(entry.id)
        if (deleted) invalidateParents()
        return deleted
    }

    override fun openInputStream(): InputStream = service.api.openDownloadStream(entry.id)

    override fun openOutputStream(): OutputStream = openOutputStream(false)

    override fun openOutputStream(append: Boolean): OutputStream {
        if (append) throw IOException("Drive files cannot be appended to")
        val context = Injekt.get<Application>()
        val staging = File.createTempFile("gdrive-", ".part", context.cacheDir)
        return UploadOnCloseStream(FileOutputStream(staging), staging)
    }

    /** Random access would mean one range request per seek; every caller has a stream path. */
    override fun createRandomAccessFile(mode: String?): UniRandomAccessFile =
        throw IOException("Random access is not supported on Google Drive files")

    private fun childEntries(): List<DriveEntry> {
        if (!entry.isFolder) return emptyList()
        return DriveCache.children(entry.id) { service.api.listChildren(entry.id) }
    }

    private fun invalidateParents() {
        entry.parents?.forEach { DriveCache.invalidate(it) }
    }

    /** Buffers locally, then pushes the whole blob to Drive once the writer is done. */
    private inner class UploadOnCloseStream(
        stream: OutputStream,
        private val staging: File,
    ) : FilterOutputStream(stream) {
        private var closed = false

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
        }

        override fun close() {
            if (closed) return
            closed = true
            try {
                super.close()
                val mime = (entry.mimeType ?: mimeOf(entry.name.orEmpty())).toMediaType()
                service.api.uploadContent(entry.id, staging.asRequestBody(mime))
                service.api.getEntry(entry.id)?.let { entry = it }
                invalidateParents()
            } finally {
                staging.delete()
            }
        }
    }

    companion object {
        fun of(parent: UniFile?, entry: DriveEntry): DriveFile = DriveFile(parent, entry)

        /**
         * Resolves `gdrive://<fileId>` back into a handle.
         *
         * Deliberately offline: [UniFile.fromUri] runs during startup on the main
         * thread, where any network call would throw. Metadata is filled in the
         * first time the file is actually touched.
         */
        fun fromUri(uri: Uri): DriveFile? {
            if (uri.scheme != GoogleDriveService.URI_SCHEME) return null
            val id = uri.host?.takeIf { it.isNotBlank() } ?: return null
            return DriveFile(null, DriveEntry(id = id, mimeType = DriveEntry.FOLDER_MIME))
        }

        private fun mimeOf(name: String): String {
            val extension = name.substringAfterLast('.', "").lowercase(Locale.US)
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: "application/octet-stream"
        }

        private fun parseRfc3339(value: String): Long = runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(value)!!
                .time
        }.getOrDefault(0L)
    }
}

/** Short-lived listing cache; a tree walk hits the same folders repeatedly. */
private object DriveCache {
    private const val TTL_MS = 30_000L

    private val entries = HashMap<String, Pair<Long, List<DriveEntry>>>()

    @Synchronized
    fun children(folderId: String, loader: () -> List<DriveEntry>): List<DriveEntry> {
        val now = System.currentTimeMillis()
        entries[folderId]?.let { (stamp, cached) ->
            if (now - stamp < TTL_MS) return cached
        }
        val fresh = loader()
        entries[folderId] = now to fresh
        return fresh
    }

    @Synchronized
    fun invalidate(folderId: String) {
        entries.remove(folderId)
    }
}
