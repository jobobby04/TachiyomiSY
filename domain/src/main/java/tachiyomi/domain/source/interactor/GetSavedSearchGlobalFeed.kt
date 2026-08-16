package tachiyomi.domain.source.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.FeedSavedSearchRepository

@Inject
class GetSavedSearchGlobalFeed(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {

    suspend fun await(): List<SavedSearch> {
        return feedSavedSearchRepository.getGlobalFeedSavedSearch()
    }
}
