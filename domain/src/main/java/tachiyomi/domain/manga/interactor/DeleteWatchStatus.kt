package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.model.WatchStatusRequest
import tachiyomi.domain.manga.repository.MangaRepository

class DeleteWatchStatus(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(watchStatusRequest: WatchStatusRequest): Boolean {
        return mangaRepository.deleteWatchStatus(watchStatusRequest)
    }

}
