package tachiyomi.domain.source.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.FeedSavedSearchRepository

@Inject
class GetSavedSearchBySourceIdFeed(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {

    suspend fun await(sourceId: Long): List<SavedSearch> {
        return feedSavedSearchRepository.getBySourceIdFeedSavedSearch(sourceId)
    }
}
