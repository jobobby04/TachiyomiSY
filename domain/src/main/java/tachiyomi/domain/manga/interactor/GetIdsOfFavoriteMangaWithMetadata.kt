package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.manga.repository.MangaMetadataRepository

/**
 * SY-specific file
 */
@Inject
class GetIdsOfFavoriteMangaWithMetadata(
    private val mangaMetadataRepository: MangaMetadataRepository,
) {

    suspend fun await(): List<Long> {
        return mangaMetadataRepository.getIdsOfFavoriteMangaWithMetadata()
    }
}
