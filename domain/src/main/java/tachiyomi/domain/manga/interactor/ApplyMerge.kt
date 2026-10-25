package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergedMangaReference
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
        references: List<MergedMangaReference>,
    ): Manga {
        return mangaMergeRepository.applyMerge(staleMangaId, mergedManga, categoryIds, references)
    }
}
