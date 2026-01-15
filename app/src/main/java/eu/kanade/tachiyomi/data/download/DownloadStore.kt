package eu.kanade.tachiyomi.data.download

import android.content.Context
import androidx.core.content.edit
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.download.model.DownloadPriority
import tachiyomi.domain.download.repository.DownloadQueueRepository
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * This class is used to persist active downloads across application restarts.
 * Now migrates to database-backed queue for better reliability.
 */
class DownloadStore(
    context: Context,
    private val sourceManager: SourceManager = Injekt.get(),
    private val json: Json = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getChapter: GetChapter = Injekt.get(),
    private val downloadQueueRepository: DownloadQueueRepository = Injekt.get(),
) {

    /**
     * Preference file where active downloads are stored.
     * Used for migration only - new downloads go to database.
     */
    private val preferences = context.getSharedPreferences("active_downloads", Context.MODE_PRIVATE)

    /**
     * Counter used to keep the queue order.
     */
    private var counter = 0

    /**
     * Adds a list of downloads to the store.
     *
     * @param downloads the list of downloads to add.
     */
    fun addAll(downloads: List<Download>) {
        preferences.edit {
            downloads.forEach { putString(getKey(it), serialize(it)) }
        }
    }

    /**
     * Removes a download from the store.
     *
     * @param download the download to remove.
     */
    fun remove(download: Download) {
        preferences.edit {
            remove(getKey(download))
        }
    }

    /**
     * Removes a list of downloads from the store.
     *
     * @param downloads the download to remove.
     */
    fun removeAll(downloads: List<Download>) {
        preferences.edit {
            downloads.forEach { remove(getKey(it)) }
        }
    }

    /**
     * Removes all the downloads from the store.
     */
    fun clear() {
        preferences.edit {
            clear()
        }
    }

    /**
     * Compute the SharedPreferences key for a download using its chapter id.
     *
     * @param download The download whose chapter id is used to form the key.
     * @return The preference key as the download's chapter id string.
     */
    private fun getKey(download: Download): String {
        return download.chapter.id.toString()
    }

    /**
     * Restores the active downloads queue, performing a one-time migration from SharedPreferences to the database if needed.
     *
     * If migration is attempted and any entry fails to migrate, the function returns an empty list so the migration can be retried on the next launch; on successful migration the old SharedPreferences entries are cleared and subsequent restores are loaded from the database-backed queue.
     *
     * @return A list of Download objects reconstructed from the persistent download queue. */
    suspend fun restore(): List<Download> {
        val migrationCompleted = preferences.getBoolean("queue_migrated_to_db", false)

        // If not migrated yet, migrate SharedPreferences to database
        if (!migrationCompleted) {
            val objs = preferences.all
                .mapNotNull { it.value as? String }
                .mapNotNull { deserialize(it) }
                .sortedBy { it.order }

            if (objs.isNotEmpty()) {
                logcat(LogPriority.INFO) { "Migrating ${objs.size} downloads to database..." }
                var failed = 0
                objs.forEach { obj ->
                    try {
                        downloadQueueRepository.add(
                            mangaId = obj.mangaId,
                            chapterId = obj.chapterId,
                            priority = DownloadPriority.NORMAL.value,
                        )
                    } catch (e: Exception) {
                        failed++
                        logcat(LogPriority.ERROR, e) { "Failed to migrate download: ${obj.chapterId}" }
                    }
                }
                if (failed == 0) {
                    logcat(LogPriority.INFO) { "Migration to database completed" }
                } else {
                    logcat(LogPriority.ERROR) { "Migration incomplete ($failed failures). Will retry next launch." }
                    // Return empty list - old data remains in preferences for retry on next launch
                    return emptyList()
                }
            }

            // Mark migration as complete BEFORE clearing to prevent data loss on crash
            // Only mark complete if all inserts succeeded (checked above)
            preferences.edit {
                putBoolean("queue_migrated_to_db", true)
            }

            // Then clear old entries in a separate transaction
            // Only clear if migration fully succeeded (checked above)
            val keysToRemove = preferences.all.keys.filter { it != "queue_migrated_to_db" }
            preferences.edit {
                keysToRemove.forEach { remove(it) }
            }
        }

        // Now load from database
        val downloads = mutableListOf<Download>()
        val queueEntries = downloadQueueRepository.getPendingByPriority()

        if (queueEntries.isNotEmpty()) {
            val cachedManga = mutableMapOf<Long, Manga?>()
            for (entry in queueEntries) {
                val manga = cachedManga.getOrPut(entry.mangaId) {
                    getManga.await(entry.mangaId)
                } ?: continue
                val source = sourceManager.get(manga.source) as? HttpSource ?: continue
                val chapter = getChapter.await(entry.chapterId) ?: continue
                downloads.add(Download(source, manga, chapter))
            }
        }

        return downloads
    }

    /**
     * Converts a download to a string.
     *
     * @param download the download to serialize.
     */
    private fun serialize(download: Download): String {
        val obj = DownloadObject(download.manga.id, download.chapter.id, counter++)
        return json.encodeToString(obj)
    }

    /**
     * Restore a download from a string.
     *
     * @param string the download as string.
     */
    private fun deserialize(string: String): DownloadObject? {
        return try {
            json.decodeFromString<DownloadObject>(string)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Class used for download serialization
 *
 * @param mangaId the id of the manga.
 * @param chapterId the id of the chapter.
 * @param order the order of the download in the queue.
 */
@Serializable
private data class DownloadObject(val mangaId: Long, val chapterId: Long, val order: Int)
