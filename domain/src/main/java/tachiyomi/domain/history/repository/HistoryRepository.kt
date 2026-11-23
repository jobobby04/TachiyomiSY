package tachiyomi.domain.history.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryTombstoneKey
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.history.model.PendingHistoryDeletion

interface HistoryRepository {

    fun getHistory(query: String): Flow<List<HistoryWithRelations>>

    suspend fun getLastHistory(): HistoryWithRelations?

    suspend fun getTotalReadDuration(): Long

    suspend fun getHistoryByMangaId(mangaId: Long): List<History>

    suspend fun getHistoryByMangaIds(mangaIds: List<Long>): Map<Long, List<History>>

    // SY -->

    /**
     * Clears [historyId] from history. A removal is also queued for the server: [tombstones] — the
     * row's sync identity, read while it still had one — become `pending_history_deletion` rows
     * stamped [removedAtMs], written in the same transaction as the reset, so a removal is either
     * queued and cleared or neither.
     *
     * [removedAtMs] is the moment of the removal in epoch milliseconds, or 0 when the configured
     * sync cannot deliver a tombstone: nothing is queued then.
     */
    // SY <--
    suspend fun resetHistory(
        historyId: Long,
        // SY -->
        tombstones: Collection<HistoryTombstoneKey> = emptyList(),
        removedAtMs: Long = 0,
        // SY <--
    )

    /** The per-manga removal: [resetHistory] over every row of [mangaId]. */
    suspend fun resetHistoryByMangaId(
        mangaId: Long,
        // SY -->
        tombstones: Collection<HistoryTombstoneKey> = emptyList(),
        removedAtMs: Long = 0,
        // SY <--
    )

    /** "Clear all history": [resetHistory] over every row. */
    suspend fun deleteAllHistory(
        // SY -->
        tombstones: Collection<HistoryTombstoneKey> = emptyList(),
        removedAtMs: Long = 0,
        // SY <--
    ): Boolean

    suspend fun deleteResetHistory()

    suspend fun upsertHistory(historyUpdate: HistoryUpdate)

    // SY -->
    suspend fun upsertAllHistory(historyUpdate: List<HistoryUpdate>)

    /** The sync identity of every history row, read before the rows are cleared. */
    suspend fun getHistoryTombstoneKeys(): List<HistoryTombstoneKey>

    /** The sync identity of the history rows of [mangaId], read before the rows are cleared. */
    suspend fun getHistoryTombstoneKeys(mangaId: Long): List<HistoryTombstoneKey>

    /** Every queued history tombstone, oldest queue row first. */
    suspend fun getPendingHistoryDeletions(): List<PendingHistoryDeletion>

    /** Drops the queue rows the server confirmed, and only those. */
    suspend fun clearPendingHistoryDeletion(ids: Collection<Long>)

    /**
     * Drops every queued tombstone a configuration change stranded (another service selected, history
     * sync turned off, a host that is not v2). Their removals stand.
     */
    suspend fun clearAllPendingHistoryDeletions()

    /**
     * Drops the history rows of [chapterIds] the server no longer has, keeping a row read after [at] (Unix seconds;
     * 0 for a tombstone that carries no action time).
     */
    suspend fun deleteHistory(chapterIds: Collection<Long>, at: Long = 0)
    // SY <--
}
