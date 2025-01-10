package tachiyomi.domain.watcher.interactor

import tachiyomi.domain.watcher.model.ExternalWatcherRequest
import tachiyomi.domain.watcher.repository.ExternalWatcherRepository

class RemoveFromExternalWatcher(
    private val externalWatcherRepository: ExternalWatcherRepository,
) {

    suspend fun await(externalWatcherRequest: ExternalWatcherRequest): Boolean {
        return externalWatcherRepository.removeFromExternalWatcher(externalWatcherRequest)
    }

}
