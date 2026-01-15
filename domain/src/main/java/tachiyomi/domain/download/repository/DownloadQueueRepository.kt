package tachiyomi.domain.download.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.download.model.DownloadErrorType
import tachiyomi.domain.download.model.DownloadPriority
import tachiyomi.domain.download.model.DownloadQueueEntry
import tachiyomi.domain.download.model.DownloadQueueStatus

interface DownloadQueueRepository {

    /**
     * Retrieves pending downloads ordered by priority, highest priority first.
     *
     * @return A list of pending DownloadQueueEntry objects ordered by priority (higher priority entries appear before lower priority ones).
     */
    suspend fun getPendingByPriority(): List<DownloadQueueEntry>

    /**
     * Retrieves pending download queue entries that are eligible for a retry according to exponential backoff scheduling.
     *
     * Entries whose backoff window has not yet elapsed are excluded.
     *
     * @return A list of pending DownloadQueueEntry objects that are currently eligible for download attempts after applying exponential backoff.
     */
    suspend fun getPendingWithBackoff(): List<DownloadQueueEntry>

    /**
     * Retrieve all download queue entries regardless of their status.
     *
     * @return A list containing every DownloadQueueEntry in the queue.
     */
    suspend fun getAll(): List<DownloadQueueEntry>

    /**
     * Provides a stream of all download queue entries.
     *
     * @return A Flow that emits the current list of DownloadQueueEntry objects whenever the queue changes.
     */
    fun getAllAsFlow(): Flow<List<DownloadQueueEntry>>

    /**
     * Retrieves the download queue entry for the given chapter ID.
     *
     * @param chapterId The chapter's database ID.
     * @return The matching DownloadQueueEntry, or `null` if no entry exists.
     */
    suspend fun getByChapterId(chapterId: Long): DownloadQueueEntry?

    /**
     * Retrieves all download queue entries associated with the given manga.
     *
     * @param mangaId The ID of the manga whose downloads to retrieve.
     * @return A list of download queue entries for the manga; empty if none exist.
     */
    suspend fun getByMangaId(mangaId: Long): List<DownloadQueueEntry>

    /**
     * Adds a chapter download to the queue.
     *
     * @param mangaId ID of the manga the chapter belongs to.
     * @param chapterId ID of the chapter to enqueue.
     * @param priority Download priority; defaults to `DownloadPriority.NORMAL.value`.
     * @return The new entry ID, or `null` if the chapter is already in the queue.
     */
    suspend fun add(
        mangaId: Long,
        chapterId: Long,
        priority: Int = DownloadPriority.NORMAL.value,
    ): Long?

    /**
     * Adds multiple downloads to the queue.
     *
     * @param entries List of pairs where the first element is the `mangaId` and the second is the `chapterId`.
     * @param priority Priority value to assign to all added entries.
     */
    suspend fun addAll(entries: List<Pair<Long, Long>>, priority: Int = DownloadPriority.NORMAL.value)

    /**
     * Update the status of a download queue entry.
     *
     * @param id The ID of the queue entry to update.
     * @param status The new download status to set.
     * @param lastAttemptAt Optional epoch millis timestamp of the last attempt; if null the timestamp is left unchanged.
     * @param lastErrorMessage Optional error message to record for the entry; if null the error message is left unchanged.
     */
    suspend fun updateStatus(
        id: Long,
        status: DownloadQueueStatus,
        lastAttemptAt: Long? = null,
        lastErrorMessage: String? = null,
    )

    /**
     * Records a download failure for the specified chapter.
     *
     * Stores the provided error message and error type for the chapter, increments its retry/count state,
     * and updates any relevant last-attempt timestamp used for retry/backoff handling.
     *
     * @param chapterId ID of the chapter whose download failed.
     * @param errorMessage Human-readable description of the failure.
     * @param errorType Categorized type of the download error.
     */
    suspend fun recordFailure(
        chapterId: Long,
        errorMessage: String,
        errorType: DownloadErrorType,
    )

    /**
     * Marks the download entry for the given chapter as completed.
     *
     * @param chapterId The id of the chapter whose download should be marked completed.
     */
    suspend fun markCompleted(chapterId: Long)

    /**
     * Updates the priority of a download queue entry.
     *
     * @param id The download queue entry ID.
     * @param priority The new priority value to assign to the entry.
     */
    suspend fun updatePriority(id: Long, priority: Int)

    /**
     * Removes the download queue entry for the specified chapter.
     *
     * @param chapterId The ID of the chapter whose download entry will be removed.
     */
    suspend fun removeByChapterId(chapterId: Long)

    /**
     * Removes a download queue entry by its entry ID.
     *
     * @param id The ID of the download queue entry to remove.
     */
    suspend fun removeById(id: Long)

    /**
     * Removes all download queue entries associated with the given manga ID.
     *
     * @param mangaId The ID of the manga whose download entries should be removed.
     */
    suspend fun removeByMangaId(mangaId: Long)

    /**
     * Removes all entries marked as completed from the download queue.
     */
    suspend fun clearCompleted()

    /**
     * Removes all entries from the download queue regardless of their status.
     */
    suspend fun clearAll()

    /**
     * Reset failed download queue entries back to pending so they can be retried.
     *
     * Only entries currently marked as failed are transitioned to the pending state.
     */
    suspend fun resetFailedToPending()

    /**
     * Counts download queue entries with the specified status.
     *
     * @param status The status to filter queue entries by.
     * @return The number of queue entries that have the specified status.
     */
    suspend fun countByStatus(status: DownloadQueueStatus): Long

    /**
     * Reset downloads that have been in DOWNLOADING status longer than the given threshold.
     *
     * Downloads whose last state change indicates they have been downloading for more than
     * `thresholdMillis` will be moved back to a pending state so they can be retried.
     *
     * @param thresholdMillis Threshold in milliseconds used to consider a download "stuck". Default is 30 minutes (30 * 60 * 1000).
     */
    suspend fun resetStuckDownloads(thresholdMillis: Long = 30 * 60 * 1000) // 30 minutes
}
