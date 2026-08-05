package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.repository.MangaRepository

class GetReadMangaIds(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(): Set<Long> {
        return mangaRepository.getReadMangaIds()
    }
}
