package tachiyomi.domain.external_watcher.interactor

import tachiyomi.domain.external_watcher.repository.ExternalWatcherRepository

class DisableExternalWatcher(
    private val externalWatcherRepository: ExternalWatcherRepository,
) {

    suspend fun await(fcmToken: String): Boolean {
        return externalWatcherRepository.disableExternalWatcher(fcmToken)
    }

}
