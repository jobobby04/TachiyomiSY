package tachiyomi.domain.external_watcher.interactor

import tachiyomi.domain.external_watcher.model.ExternalWatcherRequest
import tachiyomi.domain.external_watcher.repository.ExternalWatcherRepository

class RemoveFromExternalWatcher(
    private val externalWatcherRepository: ExternalWatcherRepository,
) {

    suspend fun await(externalWatcherRequest: ExternalWatcherRequest): Boolean {
        return externalWatcherRepository.removeFromExternalWatcher(externalWatcherRequest)
    }

}
