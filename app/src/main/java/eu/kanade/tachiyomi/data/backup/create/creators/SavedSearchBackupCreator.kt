package eu.kanade.tachiyomi.data.backup.create.creators

import app.cash.sqldelight.async.coroutines.awaitAsList
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import eu.kanade.tachiyomi.data.backup.models.backupSavedSearchMapper
import tachiyomi.data.Database

@Inject
class SavedSearchBackupCreator(
    private val database: Database,
) {

    suspend operator fun invoke(): List<BackupSavedSearch> {
        return database.saved_searchQueries
            .selectAll(backupSavedSearchMapper)
            .awaitAsList()
    }
}
