package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import exh.metadata.sql.models.SearchTitle
import tachiyomi.domain.manga.repository.MangaMetadataRepository

@Inject
class GetSearchTitles(
    private val mangaMetadataRepository: MangaMetadataRepository,
) {

    suspend fun await(mangaId: Long): List<SearchTitle> {
        return mangaMetadataRepository.getTitlesById(mangaId)
    }
}
