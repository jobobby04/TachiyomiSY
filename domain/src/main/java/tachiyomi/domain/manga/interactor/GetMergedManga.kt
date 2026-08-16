package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaMergeRepository

@Inject
class GetMergedManga(
    private val mangaMergeRepository: MangaMergeRepository,
) {

    suspend fun await(): List<Manga> {
        return try {
            mangaMergeRepository.getMergedManga()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    suspend fun subscribe(): Flow<List<Manga>> {
        return mangaMergeRepository.subscribeMergedManga()
    }
}
