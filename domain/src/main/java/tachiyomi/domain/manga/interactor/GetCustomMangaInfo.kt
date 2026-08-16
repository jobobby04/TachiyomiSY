package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.manga.repository.CustomMangaRepository

@Inject
class GetCustomMangaInfo(
    private val customMangaRepository: CustomMangaRepository,
) {

    fun get(mangaId: Long) = customMangaRepository.get(mangaId)
}
