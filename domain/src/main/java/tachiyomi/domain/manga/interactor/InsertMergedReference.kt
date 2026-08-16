package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.manga.repository.MangaMergeRepository

@Inject
class InsertMergedReference(
    private val mangaMergedRepository: MangaMergeRepository,
) {

    suspend fun await(reference: MergedMangaReference): Long? {
        return mangaMergedRepository.insert(reference)
    }

    suspend fun awaitAll(references: List<MergedMangaReference>) {
        mangaMergedRepository.insertAll(references)
    }
}
