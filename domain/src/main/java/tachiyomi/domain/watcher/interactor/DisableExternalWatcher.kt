package tachiyomi.domain.watcher.interactor

import tachiyomi.domain.watcher.repository.ExternalWatcherRepository

class DisableExternalWatcher(
    private val externalWatcherRepository: ExternalWatcherRepository,
) {

    suspend fun await(fcmToken: String): Boolean {
        return externalWatcherRepository.disableExternalWatcher(fcmToken)
    }
}
