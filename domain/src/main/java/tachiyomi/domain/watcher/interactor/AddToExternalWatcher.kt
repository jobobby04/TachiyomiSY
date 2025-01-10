package tachiyomi.domain.watcher.interactor

import tachiyomi.domain.watcher.model.ExternalWatcherRequest
import tachiyomi.domain.watcher.repository.ExternalWatcherRepository

class AddToExternalWatcher(
    private val externalWatcherRepository: ExternalWatcherRepository,
) {

    suspend fun await(externalWatcherRequest: ExternalWatcherRequest): Boolean {
        return externalWatcherRepository.addToExternalWatcher(externalWatcherRequest)
    }
}
