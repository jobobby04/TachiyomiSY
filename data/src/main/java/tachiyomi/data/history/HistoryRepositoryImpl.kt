package tachiyomi.data.history

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryTombstoneKey
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.history.model.PendingHistoryDeletion
import tachiyomi.domain.history.repository.HistoryRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class HistoryRepositoryImpl(
    private val database: Database,
) : HistoryRepository {

    override fun getHistory(query: String): Flow<List<HistoryWithRelations>> {
        return database.historyViewQueries
            .history(query, HistoryMapper::mapHistoryWithRelations)
            .subscribeToList()
    }

    override suspend fun getLastHistory(): HistoryWithRelations? {
        return database.historyViewQueries
            .getLatestHistory(HistoryMapper::mapHistoryWithRelations)
            .awaitAsOneOrNull()
    }

    override suspend fun getTotalReadDuration(): Long {
        return database.historyQueries
            .getReadDuration()
            .awaitAsOne()
    }

    override suspend fun getHistoryByMangaId(mangaId: Long): List<History> {
        return database.historyQueries
            .getHistoryByMangaId(mangaId, HistoryMapper::mapHistory)
            .awaitAsList()
    }

    override suspend fun getHistoryByMangaIds(mangaIds: List<Long>): Map<Long, List<History>> {
        return database.historyQueries
            .getHistoryByMangaIds(mangaIds) { mangaId, id, chapterId, readAt, readDuration ->
                mangaId to HistoryMapper.mapHistory(id, chapterId, readAt, readDuration)
            }
            .awaitAsList()
            .groupBy({ it.first }, { it.second })
    }

    override suspend fun resetHistory(
        historyId: Long,
        tombstones: Collection<HistoryTombstoneKey>,
        removedAtMs: Long,
    ) {
        try {
            // SY -->
            // The tombstone is written before the reset and in the same transaction: a removal is
            // either queued and cleared, or neither.
            database.transaction {
                queueHistoryTombstones(tombstones, removedAtMs)
                database.historyQueries.resetHistoryById(historyId)
            }
            // SY <--
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun resetHistoryByMangaId(
        mangaId: Long,
        tombstones: Collection<HistoryTombstoneKey>,
        removedAtMs: Long,
    ) {
        try {
            // SY -->
            database.transaction {
                queueHistoryTombstones(tombstones, removedAtMs)
                database.historyQueries.resetHistoryByMangaId(mangaId)
            }
            // SY <--
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun deleteAllHistory(
        tombstones: Collection<HistoryTombstoneKey>,
        removedAtMs: Long,
    ): Boolean {
        return try {
            // SY -->
            database.transaction {
                queueHistoryTombstones(tombstones, removedAtMs)
                database.historyQueries.removeAllHistory()
            }
            // SY <--
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
            false
        }
    }

    override suspend fun deleteResetHistory() {
        database.historyQueries.removeResettedHistory()
    }

    override suspend fun upsertHistory(historyUpdate: HistoryUpdate) {
        // SY -->
        partialUpdate(historyUpdate)
        // SY <--
    }

    // SY -->
    override suspend fun upsertAllHistory(historyUpdate: List<HistoryUpdate>) {
        partialUpdate(*historyUpdate.toTypedArray())
    }

    override suspend fun getHistoryTombstoneKeys(): List<HistoryTombstoneKey> {
        return database.historyQueries
            .getHistoryTombstoneKeys { chapterId, mangaSourceId, mangaUrl, chapterUrl ->
                HistoryTombstoneKey(chapterId, mangaSourceId, mangaUrl, chapterUrl)
            }
            .awaitAsList()
    }

    override suspend fun getHistoryTombstoneKeys(mangaId: Long): List<HistoryTombstoneKey> {
        return database.historyQueries
            .getHistoryTombstoneKeysByMangaId(mangaId) { chapterId, mangaSourceId, mangaUrl, chapterUrl ->
                HistoryTombstoneKey(chapterId, mangaSourceId, mangaUrl, chapterUrl)
            }
            .awaitAsList()
    }

    override suspend fun deleteHistory(chapterIds: Collection<Long>, at: Long) {
        database.historyQueries.deleteHistoryByChapterIds(chapterIds = chapterIds, at = at)
    }

    override suspend fun getPendingHistoryDeletions(): List<PendingHistoryDeletion> {
        return database.historyQueries
            .getPendingHistoryDeletions { id, sourceId, mangaUrl, chapterUrl, removedAtMs ->
                PendingHistoryDeletion(id, sourceId, mangaUrl, chapterUrl, removedAtMs)
            }
            .awaitAsList()
    }

    override suspend fun clearPendingHistoryDeletion(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        // One bind variable per id, so a whole queue needs chunking to fit SQLite's variable limit;
        // the transaction keeps the chunks all-or-nothing.
        database.transaction {
            ids.chunked(MAX_BOUND_IDS).forEach { database.historyQueries.clearPendingHistoryDeletion(it) }
        }
    }

    override suspend fun clearAllPendingHistoryDeletions() {
        // Dispatched here rather than at the call sites: one of them is a Compose settings callback on
        // Main, and this statement touches every queued row.
        withContext(Dispatchers.IO) {
            database.historyQueries.clearAllPendingHistoryDeletions()
        }
    }

    /**
     * Writes the tombstones of one removal, [removedAtMs] being 0 for a removal no server can be told
     * about. Must run inside the removal's transaction, so it is not a statement of its own.
     */
    private suspend fun queueHistoryTombstones(tombstones: Collection<HistoryTombstoneKey>, removedAtMs: Long) {
        if (removedAtMs == 0L) return
        tombstones.forEach { tombstone ->
            database.historyQueries.queuePendingHistoryDeletion(
                sourceId = tombstone.mangaSourceId,
                mangaUrl = tombstone.mangaUrl,
                chapterUrl = tombstone.chapterUrl,
                removedAtMs = removedAtMs,
            )
        }
    }

    private suspend fun partialUpdate(vararg historyUpdates: HistoryUpdate) {
        try {
            database.transaction {
                historyUpdates.forEach { historyUpdate ->
                    database.historyQueries.upsert(
                        chapterId = historyUpdate.chapterId,
                        readAt = historyUpdate.readAt,
                        readDuration = historyUpdate.sessionReadDuration,
                    )
                    // SY -->
                    // A read supersedes a removal the server has not been told about yet: the queued
                    // tombstone goes with the write, so it is never sent over the new read.
                    database.historyQueries.deletePendingHistoryDeletionByChapterId(historyUpdate.chapterId)
                    // SY <--
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }
    // SY <--
}

// SY -->

/** Bind ids per statement: SQLite allows 999 variables before Android 11, 32766 after. */
private const val MAX_BOUND_IDS = 900
// SY <--
