package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.MergeSettings
import tachiyomi.domain.manga.repository.MangaMergeRepository

/**
 * SY-specific file
 */
@Inject
class GetMergeSettings(
    private val mangaMergeRepository: MangaMergeRepository,
) {
    suspend fun await(id: Long): MergeSettings? {
        return try {
            mangaMergeRepository.getMergeSettings(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    suspend fun subscribe(id: Long): Flow<MergeSettings?> {
        return mangaMergeRepository.subscribeMergeSettings(id)
    }
}
