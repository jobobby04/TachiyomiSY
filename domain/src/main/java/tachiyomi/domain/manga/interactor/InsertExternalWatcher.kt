package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.model.ExternalWatcherRequest
import tachiyomi.domain.manga.repository.MangaRepository

class InsertExternalWatcher(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(externalWatcherRequest: ExternalWatcherRequest): Boolean {
        return mangaRepository.insertExternalWatcher(externalWatcherRequest)
    }

}
