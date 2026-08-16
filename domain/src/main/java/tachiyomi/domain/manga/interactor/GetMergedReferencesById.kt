package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.manga.repository.MangaMergeRepository

@Inject
class GetMergedReferencesById(
    private val mangaMergeRepository: MangaMergeRepository,
) {

    suspend fun await(id: Long): List<MergedMangaReference> {
        return try {
            mangaMergeRepository.getReferencesById(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    suspend fun subscribe(id: Long): Flow<List<MergedMangaReference>> {
        return mangaMergeRepository.subscribeReferencesById(id)
    }
}
