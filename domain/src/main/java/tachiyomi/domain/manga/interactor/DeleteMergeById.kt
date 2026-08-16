package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.manga.repository.MangaMergeRepository

@Inject
class DeleteMergeById(
    private val mangaMergeRepository: MangaMergeRepository,
) {

    suspend fun await(id: Long) {
        return mangaMergeRepository.deleteById(id)
    }
}
