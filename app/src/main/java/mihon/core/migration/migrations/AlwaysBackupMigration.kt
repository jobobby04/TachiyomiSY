package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.domain.backup.service.BackupPreferences

/**
 * SY-specific file
 */
@Inject
@ContributesIntoSet(AppScope::class)
class AlwaysBackupMigration(
    private val backupPreferences: BackupPreferences,
) : Migration {
    override val version: Float = 40f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        if (backupPreferences.backupInterval.get() == 0) {
            backupPreferences.backupInterval.set(12)
        }

        return@withContext true
    }
}
