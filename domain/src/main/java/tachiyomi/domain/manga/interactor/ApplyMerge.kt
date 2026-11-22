package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergedEntry
import tachiyomi.domain.manga.repository.MangaMergeRepository

/**
 * SY-specific file
 */
@Inject
class ApplyMerge(
    private val mangaMergeRepository: MangaMergeRepository,
) {

    suspend fun await(
        staleMangaId: Long?,
        mergedManga: Manga,
        categoryIds: List<Long>,
        chapterSortMode: Int,
        infoMangaId: Long?,
        entries: List<MergedEntry>,
    ): Manga {
        return mangaMergeRepository.applyMerge(
            staleMangaId,
            mergedManga,
            categoryIds,
            chapterSortMode,
            infoMangaId,
            entries,
        )
    }
}
