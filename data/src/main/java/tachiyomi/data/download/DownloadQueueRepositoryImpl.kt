package tachiyomi.data.download

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.download.model.DownloadErrorType
import tachiyomi.domain.download.model.DownloadQueueEntry
import tachiyomi.domain.download.model.DownloadQueueStatus
import tachiyomi.domain.download.repository.DownloadQueueRepository
import tachiyomi.domain.download.service.DownloadPreferences

class DownloadQueueRepositoryImpl(
    private val handler: DatabaseHandler,
    private val downloadPreferences: DownloadPreferences,
) : DownloadQueueRepository {

    /**
     * Retrieves pending download queue entries ordered by priority.
     *
     * @return A list of DownloadQueueEntry objects sorted by priority (highest-priority first).
     */
    override suspend fun getPendingByPriority(): List<DownloadQueueEntry> {
        return handler.awaitList {
            download_queueQueries.getPendingByPriority(mapper = ::mapDownloadQueueEntry)
        }
    }

    /**
     * Fetches pending download queue entries that are eligible for retry according to backoff rules.
     *
     * @return A list of DownloadQueueEntry objects representing pending entries eligible for retry, or an empty list if none are available.
     */
    override suspend fun getPendingWithBackoff(): List<DownloadQueueEntry> {
        // Backoff filtering is now done efficiently at the SQL layer
        return handler.awaitList {
            download_queueQueries.getPendingWithBackoff(mapper = ::mapDownloadQueueEntry)
        }
    }

    /**
     * Retrieves all entries from the download queue.
     *
     * @return A list of all DownloadQueueEntry objects currently stored in the queue.
     */
    override suspend fun getAll(): List<DownloadQueueEntry> {
        return handler.awaitList {
            download_queueQueries.getAll(mapper = ::mapDownloadQueueEntry)
        }
    }

    /**
     * Observes all download queue entries as a reactive stream.
     *
     * @return A Flow that emits the current list of DownloadQueueEntry and re-emits whenever the stored entries change.
     */
    override fun getAllAsFlow(): Flow<List<DownloadQueueEntry>> {
        return handler.subscribeToList {
            download_queueQueries.getAll(mapper = ::mapDownloadQueueEntry)
        }
    }

    /**
     * Retrieves the download queue entry for the specified chapter ID.
     *
     * @param chapterId ID of the chapter to look up.
     * @return The matching DownloadQueueEntry if present, `null` otherwise.
     */
    override suspend fun getByChapterId(chapterId: Long): DownloadQueueEntry? {
        return handler.awaitOneOrNull {
            download_queueQueries.getByChapterId(chapterId, mapper = ::mapDownloadQueueEntry)
        }
    }

    /**
     * Retrieves all download queue entries for the specified manga.
     *
     * @param mangaId The database ID of the manga to filter entries by.
     * @return A list of DownloadQueueEntry associated with the given manga, or an empty list if none exist.
     */
    override suspend fun getByMangaId(mangaId: Long): List<DownloadQueueEntry> {
        return handler.awaitList {
            download_queueQueries.getByMangaId(mangaId, mapper = ::mapDownloadQueueEntry)
        }
    }

    /**
     * Adds a chapter to the download queue if no entry for the chapter already exists.
     *
     * This operation is performed atomically within a transaction to prevent race conditions.
     *
     * @param mangaId The id of the manga the chapter belongs to.
     * @param chapterId The id of the chapter to add.
     * @param priority The priority used to order the queue entry.
     * @return The id of the newly created queue entry, or `null` if an entry for the chapter already exists.
     */
    override suspend fun add(mangaId: Long, chapterId: Long, priority: Int): Long? {
        // Check if chapter is already in queue and insert atomically within a transaction
        // (per interface contract: return null if already exists)
        return handler.await(inTransaction = true) {
            // Check if entry already exists (inside transaction to prevent race condition)
            val existing = download_queueQueries.getByChapterId(
                chapterId,
                mapper = ::mapDownloadQueueEntry,
            ).executeAsOneOrNull()

            if (existing != null) {
                // Entry already exists, return null per interface contract
                null
            } else {
                // Insert new entry
                download_queueQueries.insert(
                    mangaId = mangaId,
                    chapterId = chapterId,
                    priority = priority.toLong(),
                    addedAt = System.currentTimeMillis(),
                )
                // Fetch and return the newly inserted entry's ID
                download_queueQueries.getByChapterId(
                    chapterId,
                    mapper = ::mapDownloadQueueEntry,
                ).executeAsOneOrNull()?.id
            }
        }
    }

    /**
     * Inserts multiple download queue entries in a single atomic transaction.
     *
     * All entries receive the same insertion timestamp and the provided priority.
     *
     * @param entries List of `(mangaId, chapterId)` pairs to add to the queue.
     * @param priority Priority value applied to every inserted entry.
     */
    override suspend fun addAll(entries: List<Pair<Long, Long>>, priority: Int) {
        handler.await(inTransaction = true) {
            val now = System.currentTimeMillis()
            entries.forEach { (mangaId, chapterId) ->
                download_queueQueries.insert(
                    mangaId = mangaId,
                    chapterId = chapterId,
                    priority = priority.toLong(),
                    addedAt = now,
                )
            }
        }
    }

    /**
     * Update the download queue entry's status and related timestamps/message for the given entry ID.
     *
     * @param id The entry ID to update.
     * @param status The new status to set.
     * @param lastAttemptAt Timestamp in milliseconds of the last attempt, or `null` to leave unset.
     * @param lastErrorMessage Error message to record, or `null` to clear any existing message.
     */
    override suspend fun updateStatus(
        id: Long,
        status: DownloadQueueStatus,
        lastAttemptAt: Long?,
        lastErrorMessage: String?,
    ) {
        handler.await {
            download_queueQueries.updateStatus(
                id = id,
                status = status.value,
                lastAttemptAt = lastAttemptAt,
                lastErrorMessage = lastErrorMessage,
            )
        }
    }

    /**
     * Records a failure for a queued chapter and updates its retry state or final status.
     *
     * Increments the entry's retry count and, if the error is not retryable or the retry
     * count would exceed the configured max retries, marks the entry as FAILED with the
     * provided error message and current timestamp; otherwise updates the entry to schedule
     * another retry (exponential backoff) and records the last attempt time and message.
     *
     * @param chapterId ID of the chapter whose queue entry failed.
     * @param errorMessage Human-readable error message to store with the failure.
     * @param errorType Type of the error; determines whether the failure is eligible for retry.
     */
    override suspend fun recordFailure(
        chapterId: Long,
        errorMessage: String,
        errorType: DownloadErrorType,
    ) {
        handler.await(inTransaction = true) {
            val entry = download_queueQueries.getByChapterId(
                chapterId,
                mapper = ::mapDownloadQueueEntry,
            ).executeAsOneOrNull() ?: return@await

            val newRetryCount = entry.retryCount + 1
            val maxRetries = downloadPreferences.autoDownloadMaxRetries().get()

            if (!errorType.canRetry || newRetryCount > maxRetries) {
                // Max retries exceeded or non-retryable error
                download_queueQueries.updateStatus(
                    id = entry.id,
                    status = DownloadQueueStatus.FAILED.value,
                    lastAttemptAt = System.currentTimeMillis(),
                    lastErrorMessage = errorMessage,
                )
            } else {
                // Can retry - update for exponential backoff
                download_queueQueries.updateForRetry(
                    id = entry.id,
                    lastAttemptAt = System.currentTimeMillis(),
                    lastErrorMessage = errorMessage,
                    retryCount = newRetryCount.toLong(),
                )
            }
        }
    }

    /**
     * Mark a chapter's download queue entry as completed.
     *
     * Updates the entry's status to COMPLETED, sets the last attempt timestamp to the current time,
     * and clears any last error message if the entry exists.
     *
     * @param chapterId The database ID of the chapter whose queue entry should be marked completed.
     */
    override suspend fun markCompleted(chapterId: Long) {
        handler.await(inTransaction = true) {
            val entry = download_queueQueries.getByChapterId(
                chapterId,
                mapper = ::mapDownloadQueueEntry,
            ).executeAsOneOrNull() ?: return@await

            download_queueQueries.updateStatus(
                id = entry.id,
                status = DownloadQueueStatus.COMPLETED.value,
                lastAttemptAt = System.currentTimeMillis(),
                lastErrorMessage = null,
            )
        }
    }

    /**
     * Updates the priority of a download queue entry.
     *
     * @param id The ID of the queue entry to update.
     * @param priority The new priority value to assign to the entry.
     */
    override suspend fun updatePriority(id: Long, priority: Int) {
        handler.await {
            download_queueQueries.updatePriority(
                id = id,
                priority = priority.toLong(),
            )
        }
    }

    /**
     * Removes the download queue entry associated with the given chapter ID.
     *
     * @param chapterId The ID of the chapter whose queue entry should be removed.
     */
    override suspend fun removeByChapterId(chapterId: Long) {
        handler.await {
            download_queueQueries.removeByChapterId(chapterId)
        }
    }

    /**
     * Removes the download queue entry with the given id.
     *
     * @param id ID of the download queue entry to remove.
     */
    override suspend fun removeById(id: Long) {
        handler.await {
            download_queueQueries.removeById(id)
        }
    }

    /**
     * Removes all download queue entries associated with the given manga ID.
     *
     * @param mangaId The ID of the manga whose download queue entries will be removed.
     */
    override suspend fun removeByMangaId(mangaId: Long) {
        handler.await {
            download_queueQueries.removeByMangaId(mangaId)
        }
    }

    /**
     * Removes all download queue entries whose status indicates completion.
     */
    override suspend fun clearCompleted() {
        handler.await {
            download_queueQueries.clearCompleted()
        }
    }

    /**
     * Deletes all entries from the download queue.
     */
    override suspend fun clearAll() {
        handler.await {
            download_queueQueries.clearAll()
        }
    }

    /**
     * Resets all download queue entries that are in the `FAILED` status back to `PENDING`.
     */
    override suspend fun resetFailedToPending() {
        handler.await {
            download_queueQueries.resetFailedToPending()
        }
    }

    /**
     * Count download queue entries with the given status.
     *
     * @param status The download status to filter by.
     * @return The number of entries with the specified status.
     */
    override suspend fun countByStatus(status: DownloadQueueStatus): Long {
        return handler.awaitOne {
            download_queueQueries.countByStatus(status.value)
        }
    }

    /**
     * Resets download queue entries considered stuck based on a time threshold.
     *
     * @param thresholdMillis Time window in milliseconds; entries whose last attempt timestamp is older than the current time minus this threshold will be reset.
     */
    override suspend fun resetStuckDownloads(thresholdMillis: Long) {
        handler.await {
            download_queueQueries.resetStuckDownloads(thresholdMillis)
        }
    }

    /**
     * Maps raw database row fields into a DownloadQueueEntry.
     *
     * @param _id Row primary key for the queue entry.
     * @param manga_id ID of the manga associated with the entry.
     * @param chapter_id ID of the chapter associated with the entry.
     * @param priority Priority value from the database.
     * @param added_at Timestamp when the entry was added.
     * @param retry_count Number of retry attempts recorded.
     * @param last_attempt_at Timestamp of the last attempt, or `null` if none.
     * @param last_error_message Last error message, or `null` if none.
     * @param status Status string stored in the database.
     * @return A DownloadQueueEntry with fields populated; `status` is converted to DownloadQueueStatus.
     */
    private fun mapDownloadQueueEntry(
        _id: Long,
        manga_id: Long,
        chapter_id: Long,
        priority: Long,
        added_at: Long,
        retry_count: Long,
        last_attempt_at: Long?,
        last_error_message: String?,
        status: String,
    ): DownloadQueueEntry {
        return DownloadQueueEntry(
            id = _id,
            mangaId = manga_id,
            chapterId = chapter_id,
            priority = priority.toInt(),
            addedAt = added_at,
            retryCount = retry_count.toInt(),
            lastAttemptAt = last_attempt_at,
            lastErrorMessage = last_error_message,
            status = DownloadQueueStatus.fromString(status),
        )
    }
}
