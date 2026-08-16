package tachiyomi.domain.source.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.SavedSearchRepository

@Inject
class GetSavedSearchById(
    private val savedSearchRepository: SavedSearchRepository,
) {

    suspend fun await(savedSearchId: Long): SavedSearch {
        return savedSearchRepository.getById(savedSearchId)!!
    }

    suspend fun awaitOrNull(savedSearchId: Long): SavedSearch? {
        return savedSearchRepository.getById(savedSearchId)
    }
}
