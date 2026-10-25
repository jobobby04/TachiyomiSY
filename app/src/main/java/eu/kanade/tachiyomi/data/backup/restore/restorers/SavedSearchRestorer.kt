package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import exh.util.nullIfBlank
import tachiyomi.domain.search.model.SavedSearch
import tachiyomi.domain.search.repository.SavedSearchRepository

/**
 * SY-specific file
 */
@Inject
class SavedSearchRestorer(
    private val savedSearchRepository: SavedSearchRepository,
) {
    suspend fun restoreSavedSearches(backupSavedSearches: List<BackupSavedSearch>) {
        if (backupSavedSearches.isEmpty()) return

        val currentSavedSearches = savedSearchRepository.getAll()

        savedSearchRepository.insertAll(
            backupSavedSearches.filter { backupSavedSearch ->
                currentSavedSearches.none { it.source == backupSavedSearch.source && it.name == backupSavedSearch.name }
            }.map {
                SavedSearch(
                    source = it.source,
                    name = it.name,
                    query = it.query.nullIfBlank(),
                    filtersJson = it.filterList.nullIfBlank()
                        ?.takeUnless { f -> f == "[]" },
                )
            },
        )
    }
}
