package tachiyomi.domain.history.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryTombstoneKey
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.history.model.HistoryWithRelations

interface HistoryRepository {

    fun getHistory(query: String): Flow<List<HistoryWithRelations>>

    suspend fun getLastHistory(): HistoryWithRelations?

    suspend fun getTotalReadDuration(): Long

    suspend fun getHistoryByMangaId(mangaId: Long): List<History>

    suspend fun getHistoryByMangaIds(mangaIds: List<Long>): Map<Long, List<History>>

    suspend fun resetHistory(historyId: Long)

    suspend fun resetHistoryByMangaId(mangaId: Long)

    suspend fun deleteAllHistory(): Boolean

    suspend fun deleteResetHistory()

    suspend fun upsertHistory(historyUpdate: HistoryUpdate)

    // SY -->
    suspend fun upsertAllHistory(historyUpdate: List<HistoryUpdate>)

    /** The sync identity of every history row, read before the rows are cleared. */
    suspend fun getHistoryTombstoneKeys(): List<HistoryTombstoneKey>

    /** The sync identity of the history rows of [mangaId], read before the rows are cleared. */
    suspend fun getHistoryTombstoneKeys(mangaId: Long): List<HistoryTombstoneKey>

    /** Drops the history rows of [chapterIds] the server no longer has. */
    suspend fun deleteHistory(chapterIds: Collection<Long>)
    // SY <--
}
