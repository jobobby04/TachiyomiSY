package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.manga.repository.MangaRepository

@Inject
class DeleteMangaById(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(id: Long) {
        return mangaRepository.deleteManga(id)
    }
}
