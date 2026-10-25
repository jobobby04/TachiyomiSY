package eu.kanade.tachiyomi.data.backup.create.creators

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import tachiyomi.domain.search.repository.SavedSearchRepository

/**
 * SY-specific file
 */
@Inject
class SavedSearchBackupCreator(
    private val savedSearchRepository: SavedSearchRepository,
) {

    suspend operator fun invoke(): List<BackupSavedSearch> {
        return savedSearchRepository.getAll().map {
            BackupSavedSearch(
                source = it.source,
                name = it.name,
                query = it.query.orEmpty(),
                filterList = it.filtersJson ?: "[]",
            )
        }
    }
}
