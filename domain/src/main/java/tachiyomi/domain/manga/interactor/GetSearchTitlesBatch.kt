package tachiyomi.domain.manga.interactor

import exh.metadata.sql.models.SearchTitle
import tachiyomi.domain.manga.repository.MangaMetadataRepository

class GetSearchTitlesBatch(
    private val mangaMetadataRepository: MangaMetadataRepository,
) {
    /**
     * Fetches search titles for the given manga IDs in a single batch.
     *
     * @param mangaIds The list of manga IDs to retrieve titles for.
     * @return A map from each manga ID to its corresponding list of `SearchTitle`.
     */
    suspend fun await(mangaIds: List<Long>): Map<Long, List<SearchTitle>> {
        return mangaMetadataRepository.getTitlesByIds(mangaIds)
    }
}
