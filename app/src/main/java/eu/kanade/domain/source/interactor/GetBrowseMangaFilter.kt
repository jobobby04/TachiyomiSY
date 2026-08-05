package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.domain.manga.interactor.GetReadMangaIds
import tachiyomi.domain.manga.model.Manga

class GetBrowseMangaFilter(
    private val sourcePreferences: SourcePreferences,
    private val getReadMangaIds: GetReadMangaIds,
) {

    suspend operator fun invoke(filterReadItems: Boolean = true): BrowseMangaFilter {
        val hideReadItems = filterReadItems && sourcePreferences.hideReadItems.get()
        return BrowseMangaFilter(
            hideInLibraryItems = sourcePreferences.hideInLibraryItems.get(),
            readMangaIds = if (hideReadItems) getReadMangaIds.await() else emptySet(),
        )
    }
}

data class BrowseMangaFilter(
    private val hideInLibraryItems: Boolean,
    private val readMangaIds: Set<Long>,
) {

    fun isVisible(manga: Manga): Boolean {
        return isVisible(manga.id, manga.favorite)
    }

    internal fun isVisible(mangaId: Long, isFavorite: Boolean): Boolean {
        return (!hideInLibraryItems || !isFavorite) && mangaId !in readMangaIds
    }
}
