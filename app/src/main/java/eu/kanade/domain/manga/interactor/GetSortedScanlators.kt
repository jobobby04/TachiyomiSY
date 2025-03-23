package eu.kanade.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.manga.model.SortedScanlator

class GetSortedScanlators(
    private val handler: DatabaseHandler
) {
    suspend fun await(mangaId: Long): Set<SortedScanlator> {
        return handler.awaitList {
            sorted_scanlatorsQueries.getSortedScanlatorsByMangaId(mangaId)
        }.map {
            SortedScanlator(it.scanlator, it.rank)
        }.toSet()
    }

    fun subscribe(mangaId: Long): Flow<Set<SortedScanlator>> {
        return handler.subscribeToList {
            sorted_scanlatorsQueries.getSortedScanlatorsByMangaId(mangaId)
        }
            .map { sortedScanlators -> sortedScanlators.map { SortedScanlator(it.scanlator, it.rank) }.toSet() }
    }
}
