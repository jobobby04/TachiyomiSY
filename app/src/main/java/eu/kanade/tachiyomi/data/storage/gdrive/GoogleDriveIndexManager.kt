package eu.kanade.tachiyomi.data.storage.gdrive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import java.io.InputStream

@Serializable
data class DriveDownloadIndex(
    val version: Int = 1,
    val lastUpdated: Long = System.currentTimeMillis(),
    // sourceDirName.lowercase() -> (mangaDirName -> list of chapterDirNames)
    val sources: Map<String, Map<String, List<String>>> = emptyMap(),
)

/**
 * Manages the single `downloads_index.json` file stored in the Google Drive root folder.
 *
 * This allows instant 1-request synchronization of the entire cloud download library
 * across devices, avoiding recursive directory scanning on large libraries.
 */
class GoogleDriveIndexManager(
    private val service: GoogleDriveService,
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()
    private var currentIndex = DriveDownloadIndex()
    private var indexFileId: String? = null
    private var syncJob: Job? = null

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /**
     * Loads the index from Google Drive root directory in a single GET request.
     * Returns null if the file does not exist yet or drive is not signed in.
     */
    suspend fun loadRemoteIndex(): DriveDownloadIndex? = mutex.withLock {
        if (!service.isSignedIn) return null
        try {
            val rootId = service.ensureRootFolder()
            val children = service.api.listChildren(rootId)
            val fileEntry = children.firstOrNull { it.name == INDEX_FILENAME && !it.isFolder }
            if (fileEntry != null) {
                indexFileId = fileEntry.id
                val stream: InputStream = service.api.openDownloadStream(fileEntry.id)
                val content = stream.use { it.reader().readText() }
                val parsed = json.decodeFromString<DriveDownloadIndex>(content)
                currentIndex = parsed
                logcat(LogPriority.INFO) { "Google Drive download index loaded: ${parsed.sources.size} sources" }
                return parsed
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to load Google Drive download index" }
        }
        return null
    }

    fun getCachedIndex(): DriveDownloadIndex = currentIndex

    fun setIndex(index: DriveDownloadIndex) {
        currentIndex = index
        scheduleSave()
    }

    fun addChapter(sourceDirName: String, mangaDirName: String, chapterDirName: String) {
        scope.launch {
            mutex.withLock {
                val sourceKey = sourceDirName.lowercase()
                val currentSources = currentIndex.sources.toMutableMap()
                val currentMangaMap = currentSources[sourceKey]?.toMutableMap() ?: mutableMapOf()
                val currentChapters = currentMangaMap[mangaDirName]?.toMutableSet() ?: mutableSetOf()

                currentChapters.add(chapterDirName)
                currentMangaMap[mangaDirName] = currentChapters.toList()
                currentSources[sourceKey] = currentMangaMap

                currentIndex = currentIndex.copy(
                    lastUpdated = System.currentTimeMillis(),
                    sources = currentSources,
                )
            }
            scheduleSave()
        }
    }

    fun removeChapters(sourceDirName: String, mangaDirName: String, chapterDirNames: List<String>) {
        scope.launch {
            mutex.withLock {
                val sourceKey = sourceDirName.lowercase()
                val currentSources = currentIndex.sources.toMutableMap()
                val currentMangaMap = currentSources[sourceKey]?.toMutableMap() ?: return@launch
                val currentChapters = currentMangaMap[mangaDirName]?.toMutableSet() ?: return@launch

                currentChapters.removeAll(chapterDirNames.toSet())
                if (currentChapters.isEmpty()) {
                    currentMangaMap.remove(mangaDirName)
                } else {
                    currentMangaMap[mangaDirName] = currentChapters.toList()
                }

                if (currentMangaMap.isEmpty()) {
                    currentSources.remove(sourceKey)
                } else {
                    currentSources[sourceKey] = currentMangaMap
                }

                currentIndex = currentIndex.copy(
                    lastUpdated = System.currentTimeMillis(),
                    sources = currentSources,
                )
            }
            scheduleSave()
        }
    }

    fun removeManga(sourceDirName: String, mangaDirName: String) {
        scope.launch {
            mutex.withLock {
                val sourceKey = sourceDirName.lowercase()
                val currentSources = currentIndex.sources.toMutableMap()
                val currentMangaMap = currentSources[sourceKey]?.toMutableMap() ?: return@launch

                currentMangaMap.remove(mangaDirName)
                if (currentMangaMap.isEmpty()) {
                    currentSources.remove(sourceKey)
                } else {
                    currentSources[sourceKey] = currentMangaMap
                }

                currentIndex = currentIndex.copy(
                    lastUpdated = System.currentTimeMillis(),
                    sources = currentSources,
                )
            }
            scheduleSave()
        }
    }

    private fun scheduleSave() {
        syncJob?.cancel()
        syncJob = scope.launch {
            delay(1500) // Debounce saves by 1.5s
            saveToRemote()
        }
    }

    private suspend fun saveToRemote() = mutex.withLock {
        if (!service.isSignedIn) return
        try {
            val rootId = service.ensureRootFolder()
            val content = json.encodeToString(currentIndex)
            val body = content.toRequestBody(JSON_MIME)

            var fileId = indexFileId
            if (fileId == null) {
                val children = service.api.listChildren(rootId)
                val existing = children.firstOrNull { it.name == INDEX_FILENAME && !it.isFolder }
                fileId = existing?.id ?: service.api.createFile(rootId, INDEX_FILENAME, "application/json").id
                indexFileId = fileId
            }
            service.api.uploadContent(fileId, body)
            logcat(LogPriority.INFO) { "Google Drive download index saved to cloud (${content.length} bytes)" }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to save Google Drive download index to cloud" }
        }
    }

    companion object {
        const val INDEX_FILENAME = "downloads_index.json"
        private val JSON_MIME = "application/json; charset=utf-8".toMediaType()
    }
}
