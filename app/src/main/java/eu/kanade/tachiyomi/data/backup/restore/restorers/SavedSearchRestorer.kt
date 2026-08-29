package eu.kanade.tachiyomi.data.backup.restore.restorers

import app.cash.sqldelight.async.coroutines.awaitAsList
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import exh.util.nullIfBlank
import tachiyomi.data.Database
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SavedSearchRestorer(
    private val database: Database = Injekt.get(),
) {
    suspend fun restoreSavedSearches(backupSavedSearches: List<BackupSavedSearch>) {
        if (backupSavedSearches.isEmpty()) return

        val currentSavedSearches = database.saved_searchQueries
            .selectNamesAndSources()
            .awaitAsList()

        database.transaction {
            backupSavedSearches.filter { backupSavedSearch ->
                currentSavedSearches.none { it.source == backupSavedSearch.source && it.name == backupSavedSearch.name }
            }.forEach {
                database.saved_searchQueries.insert(
                    source = it.source,
                    name = it.name,
                    query = it.query.nullIfBlank(),
                    filtersJson = it.filterList.nullIfBlank()
                        ?.takeUnless { it == "[]" },
                )
            }
        }
    }
}
