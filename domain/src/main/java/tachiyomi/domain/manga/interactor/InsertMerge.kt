package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.manga.repository.MangaMergeRepository

/**
 * SY-specific file
 */
@Inject
class InsertMerge(
    private val mangaMergeRepository: MangaMergeRepository,
) {
    suspend fun await(mangaId: Long, chapterSortMode: Int, infoMangaId: Long?) {
        mangaMergeRepository.insertMerge(mangaId, chapterSortMode, infoMangaId)
    }
}
