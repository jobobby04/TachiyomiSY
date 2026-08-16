package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.manga.repository.FavoritesEntryRepository

@Inject
class DeleteFavoriteEntries(
    private val favoriteEntryRepository: FavoritesEntryRepository,
) {

    suspend fun await() {
        return favoriteEntryRepository.deleteAll()
    }
}
