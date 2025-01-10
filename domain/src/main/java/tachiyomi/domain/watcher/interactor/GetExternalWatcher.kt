package tachiyomi.domain.watcher.interactor

import tachiyomi.domain.watcher.repository.ExternalWatcherRepository

class GetExternalWatcher(
    private val externalWatcherRepository: ExternalWatcherRepository,
) {

    suspend fun await(mangaId: Long, fcmToken: String): Boolean? {
        return externalWatcherRepository.getExternalWatcher(mangaId, fcmToken)
    }

}
