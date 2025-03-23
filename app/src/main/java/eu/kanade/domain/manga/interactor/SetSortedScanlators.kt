package eu.kanade.domain.manga.interactor

import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.manga.model.SortedScanlator

class SetSortedScanlators(
    private val handler: DatabaseHandler
) {

    suspend fun await(mangaId: Long, sortedScanlators: Set<SortedScanlator>) {
        handler.await(inTransaction = true) {
            val currentlySortedScanlators = handler.awaitList {
                sorted_scanlatorsQueries.getSortedScanlatorsByMangaId(mangaId)
            }.toSet()
            val scanlatorNamesOnly = sortedScanlators.map { it.scanlator }.toSet()
            val toRemove = currentlySortedScanlators.filterNot {
                it.scanlator in scanlatorNamesOnly
            }

            for (sortedScanlator in toRemove) {
                sorted_scanlatorsQueries.remove(mangaId, sortedScanlator.scanlator)
            }
            for (sortedScanlator in sortedScanlators) {
                sorted_scanlatorsQueries.upsert(mangaId, sortedScanlator.scanlator, sortedScanlator.rank)
            }
        }
    }
}
