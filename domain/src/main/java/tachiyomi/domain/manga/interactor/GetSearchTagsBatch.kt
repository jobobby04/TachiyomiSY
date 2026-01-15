package tachiyomi.domain.manga.interactor

import exh.metadata.sql.models.SearchTag
import tachiyomi.domain.manga.repository.MangaMetadataRepository

class GetSearchTagsBatch(
    private val mangaMetadataRepository: MangaMetadataRepository,
) {
    /**
     * Retrieves search tags for the given manga IDs.
     *
     * @param mangaIds List of manga database IDs to fetch tags for.
     * @return A map from each manga ID to its list of associated `SearchTag`.
     */
    suspend fun await(mangaIds: List<Long>): Map<Long, List<SearchTag>> {
        return mangaMetadataRepository.getTagsByIds(mangaIds)
    }
}
