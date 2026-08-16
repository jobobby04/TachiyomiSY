package tachiyomi.domain.source.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.source.repository.SavedSearchRepository

@Inject
class DeleteSavedSearchById(
    private val savedSearchRepository: SavedSearchRepository,
) {

    suspend fun await(savedSearchId: Long) {
        return savedSearchRepository.delete(savedSearchId)
    }
}
