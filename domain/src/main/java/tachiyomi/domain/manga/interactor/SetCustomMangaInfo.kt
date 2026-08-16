package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.repository.CustomMangaRepository

@Inject
class SetCustomMangaInfo(
    private val customMangaRepository: CustomMangaRepository,
) {

    fun set(mangaInfo: CustomMangaInfo) = customMangaRepository.set(mangaInfo)
}
