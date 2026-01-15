package tachiyomi.domain.history.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.history.model.HistoryWithRelations
import java.util.Date

interface HistoryRepository {

    /**
     * Observe history items together with their related entities filtered by the provided query.
     *
     * @param query The filter text used to match history entries.
     * @return Lists of HistoryWithRelations that match the provided query.
     */
    fun getHistory(query: String): Flow<List<HistoryWithRelations>>

    /**
     * Retrieve the most recent history entry along with its related entities.
     *
     * @return The most recent `HistoryWithRelations`, or `null` if no history exists.
     */
    suspend fun getLastHistory(): HistoryWithRelations?

    /**
     * Retrieves history entries read after the provided date for auto-download evaluation.
     *
     * @param readAfter Cutoff date; only history items with a read timestamp later than this date are returned.
     * @return A list of `HistoryWithRelations` matching the cutoff, or an empty list if none exist.
     */
    suspend fun getHistoryForAutoDownload(readAfter: Date): List<HistoryWithRelations>

    /**
     * Calculates the cumulative duration of all read history entries.
     *
     * The duration is expressed in milliseconds.
     *
     * @return Total read duration in milliseconds.
     */
    suspend fun getTotalReadDuration(): Long

    /**
     * Retrieves all history entries associated with the given manga.
     *
     * @param mangaId The ID of the manga whose history entries should be returned.
     * @return A list of `History` entries for the specified manga.
     */
    suspend fun getHistoryByMangaId(mangaId: Long): List<History>

    suspend fun resetHistory(historyId: Long)

    suspend fun resetHistoryByMangaId(mangaId: Long)

    suspend fun deleteAllHistory(): Boolean

    suspend fun upsertHistory(historyUpdate: HistoryUpdate)

    // SY -->
    suspend fun upsertHistory(historyUpdates: List<HistoryUpdate>)
    // SY <--
}
