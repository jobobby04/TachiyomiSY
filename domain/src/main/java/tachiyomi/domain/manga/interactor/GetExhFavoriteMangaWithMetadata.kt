package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaMetadataRepository

@Inject
class GetExhFavoriteMangaWithMetadata(
    private val mangaMetadataRepository: MangaMetadataRepository,
) {

    suspend fun await(): List<Manga> {
        return mangaMetadataRepository.getExhFavoriteMangaWithMetadata()
    }
}
