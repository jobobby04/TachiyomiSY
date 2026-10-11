package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaMergeRepository

@Inject
class GetMergedManga(
    private val mangaMergeRepository: MangaMergeRepository,
) {

    suspend fun await(): List<Manga> {
        return try {
            mangaMergeRepository.getMergedManga()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    /**
     * Returns the entries of every merged entry, keyed by the merged entry's id.
     */
    suspend fun awaitByMergeId(): Map<Long, List<Manga>> {
        return try {
            val mangaById = mangaMergeRepository.getMergedManga().associateBy { it.id }
            mangaMergeRepository.getAllReferences()
                .groupBy({ it.mergeId }, { it.mangaId?.let(mangaById::get) })
                .mapNotNull { (mergeId, manga) -> mergeId?.let { it to manga.filterNotNull() } }
                .toMap()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyMap()
        }
    }

    suspend fun subscribe(): Flow<List<Manga>> {
        return mangaMergeRepository.subscribeMergedManga()
    }
}
