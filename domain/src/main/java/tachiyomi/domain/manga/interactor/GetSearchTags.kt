package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import exh.metadata.sql.models.SearchTag
import tachiyomi.domain.manga.repository.MangaMetadataRepository

@Inject
class GetSearchTags(
    private val mangaMetadataRepository: MangaMetadataRepository,
) {

    suspend fun await(mangaId: Long): List<SearchTag> {
        return mangaMetadataRepository.getTagsById(mangaId)
    }
}
