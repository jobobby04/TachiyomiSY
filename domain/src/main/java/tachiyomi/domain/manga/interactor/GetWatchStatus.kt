package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.repository.MangaRepository

class GetWatchStatus(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(mangaId: Long, fcmToken: String): Boolean? {
        return mangaRepository.getWatchStatus(mangaId, fcmToken)
    }

}
