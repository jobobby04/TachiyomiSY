package tachiyomi.domain.source.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.source.repository.FeedSavedSearchRepository

@Inject
class CountFeedSavedSearchBySourceId(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {

    suspend fun await(sourceId: Long): Long {
        return feedSavedSearchRepository.countBySourceId(sourceId)
    }
}
