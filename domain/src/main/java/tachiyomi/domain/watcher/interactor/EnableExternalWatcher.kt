package tachiyomi.domain.watcher.interactor

import tachiyomi.domain.watcher.repository.ExternalWatcherRepository

class EnableExternalWatcher(
    private val externalWatcherRepository: ExternalWatcherRepository,
) {

    suspend fun await(fcmToken: String, interval: Long): Boolean {
        return externalWatcherRepository.enableExternalWatcher(fcmToken, interval)
    }
}
