package tachiyomi.domain.source.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.source.repository.FeedSavedSearchRepository

/**
 * SY-specific file
 */
@Inject
class DeleteFeedSavedSearchById(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {

    suspend fun await(feedSavedSearchId: Long) {
        return feedSavedSearchRepository.delete(feedSavedSearchId)
    }
}
