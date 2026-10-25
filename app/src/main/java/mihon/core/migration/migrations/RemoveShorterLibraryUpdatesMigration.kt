package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.domain.library.service.LibraryPreferences

/**
 * SY-specific file
 */
@Inject
@ContributesIntoSet(AppScope::class)
class RemoveShorterLibraryUpdatesMigration(
    private val libraryPreferences: LibraryPreferences,
) : Migration {
    override val version: Float = 18f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        val updateInterval = libraryPreferences.autoUpdateInterval.get()
        if (updateInterval == 1 || updateInterval == 2) {
            libraryPreferences.autoUpdateInterval.set(3)
        }

        return@withContext true
    }
}
