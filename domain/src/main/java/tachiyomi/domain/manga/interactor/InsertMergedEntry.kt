package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.manga.model.MergedEntry
import tachiyomi.domain.manga.repository.MangaMergeRepository

/**
 * SY-specific file
 */
@Inject
class InsertMergedEntry(
    private val mangaMergedRepository: MangaMergeRepository,
) {

    suspend fun await(entry: MergedEntry): Long? {
        return mangaMergedRepository.insert(entry)
    }

    suspend fun awaitAll(entries: List<MergedEntry>) {
        mangaMergedRepository.insertAll(entries)
    }
}
