package tachiyomi.domain.external_watcher.interactor

import tachiyomi.domain.external_watcher.repository.ExternalWatcherRepository

class EnableExternalWatcher(
    private val externalWatcherRepository: ExternalWatcherRepository,
) {

    suspend fun await(fcmToken: String, interval: Long): Boolean {
        return externalWatcherRepository.enableExternalWatcher(fcmToken, interval)
    }

}
