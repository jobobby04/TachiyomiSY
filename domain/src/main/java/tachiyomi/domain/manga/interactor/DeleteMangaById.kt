package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.manga.model.MangaTombstoneKey
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.manga.service.MangaTombstones

/**
 * SY-specific file
 */
@Inject
class DeleteMangaById(
    private val mangaRepository: MangaRepository,
    private val mangaTombstones: MangaTombstones,
) {

    suspend fun await(id: Long) {
        // The row has to be read before it is gone, and a deletion the sync client never hears about is
        // one the other devices never see.
        val manga = mangaRepository.getMangaById(id)
        mangaRepository.deleteManga(id)
        mangaTombstones.remember(listOf(MangaTombstoneKey(manga.source, manga.url)))
    }
}
