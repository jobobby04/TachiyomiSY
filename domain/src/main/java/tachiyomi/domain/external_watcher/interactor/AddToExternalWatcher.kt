package tachiyomi.domain.external_watcher.interactor

import tachiyomi.domain.external_watcher.model.ExternalWatcherRequest
import tachiyomi.domain.external_watcher.repository.ExternalWatcherRepository

class AddToExternalWatcher(
    private val externalWatcherRepository: ExternalWatcherRepository,
) {

    suspend fun await(externalWatcherRequest: ExternalWatcherRequest): Boolean {
        return externalWatcherRepository.addToExternalWatcher(externalWatcherRequest)
    }

}
