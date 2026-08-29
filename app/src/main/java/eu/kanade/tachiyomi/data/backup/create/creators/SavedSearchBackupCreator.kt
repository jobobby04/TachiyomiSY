package eu.kanade.tachiyomi.data.backup.create.creators

import app.cash.sqldelight.async.coroutines.awaitAsList
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import eu.kanade.tachiyomi.data.backup.models.backupSavedSearchMapper
import tachiyomi.data.Database
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SavedSearchBackupCreator(
    private val database: Database = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupSavedSearch> {
        return database.saved_searchQueries
            .selectAll(backupSavedSearchMapper)
            .awaitAsList()
    }
}
