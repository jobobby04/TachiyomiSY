package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.library.service.LibraryPreferences

@Inject
@ContributesIntoSet(AppScope::class)
class RemoveShortLibraryUpdatesMigration(
    private val libraryPreferences: LibraryPreferences,
) : Migration {
    override val version: Float = 22f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val updateInterval = libraryPreferences.autoUpdateInterval.get()
        if (updateInterval in listOf(3, 4, 6, 8)) {
            libraryPreferences.autoUpdateInterval.set(12)
        }

        return@withIOContext true
    }
}
