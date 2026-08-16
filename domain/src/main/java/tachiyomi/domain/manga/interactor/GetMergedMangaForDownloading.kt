package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaMergeRepository

@Inject
class GetMergedMangaForDownloading(
    private val mangaMergeRepository: MangaMergeRepository,
) {

    suspend fun await(mergeId: Long): List<Manga> {
        return mangaMergeRepository.getMergeMangaForDownloading(mergeId)
    }
}
