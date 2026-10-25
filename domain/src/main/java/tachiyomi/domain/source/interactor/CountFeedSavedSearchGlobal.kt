package tachiyomi.domain.source.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.source.repository.FeedSavedSearchRepository

/**
 * SY-specific file
 */
@Inject
class CountFeedSavedSearchGlobal(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {

    suspend fun await(): Long {
        return feedSavedSearchRepository.countGlobal()
    }
}
