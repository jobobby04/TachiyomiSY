package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.repository.MangaRepository

@Inject
class GetReadMangaNotInLibraryView(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(): List<LibraryManga> {
        return mangaRepository.getReadMangaNotInLibraryView()
    }
}
