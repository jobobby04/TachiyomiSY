package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.manga.model.FavoriteEntry
import tachiyomi.domain.manga.repository.FavoritesEntryRepository

@Inject
class InsertFavoriteEntries(
    private val favoriteEntryRepository: FavoritesEntryRepository,
) {

    suspend fun await(entries: List<FavoriteEntry>) {
        return favoriteEntryRepository.insertAll(entries)
    }
}
