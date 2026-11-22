package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.manga.model.MergedEntry
import tachiyomi.domain.manga.repository.MangaMergeRepository

/**
 * SY-specific file
 */
@Inject
class UpdateMergedSettings(
    private val mangaMergeRepository: MangaMergeRepository,
) {
    suspend fun awaitMerge(mergeId: Long, chapterSortMode: Int, infoMangaId: Long?): Boolean {
        return mangaMergeRepository.updateMergeSettings(mergeId, chapterSortMode, infoMangaId)
    }

    suspend fun awaitAll(entries: List<MergedEntry>): Boolean {
        return mangaMergeRepository.updateAllEntries(entries)
    }
}
