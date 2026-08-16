package tachiyomi.domain.source.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.SavedSearchRepository

@Inject
class GetSavedSearchBySourceId(
    private val savedSearchRepository: SavedSearchRepository,
) {

    suspend fun await(sourceId: Long): List<SavedSearch> {
        return savedSearchRepository.getBySourceId(sourceId)
    }

    fun subscribe(sourceId: Long): Flow<List<SavedSearch>> {
        return savedSearchRepository.getBySourceIdAsFlow(sourceId)
    }
}
