package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.MergedEntry
import tachiyomi.domain.manga.repository.MangaMergeRepository

/**
 * SY-specific file
 */
@Inject
class GetMergedEntriesById(
    private val mangaMergeRepository: MangaMergeRepository,
) {

    suspend fun await(id: Long): List<MergedEntry> {
        return try {
            mangaMergeRepository.getEntriesById(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    suspend fun subscribe(id: Long): Flow<List<MergedEntry>> {
        return mangaMergeRepository.subscribeEntriesById(id)
    }
}
