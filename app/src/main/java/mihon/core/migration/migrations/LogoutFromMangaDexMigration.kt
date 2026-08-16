package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.track.TrackerManager
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

@Inject
@ContributesIntoSet(AppScope::class)
class LogoutFromMangaDexMigration(
    private val trackerManager: TrackerManager,
) : Migration {
    override val version: Float = 45f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        // Force MangaDex log out due to login flow change
        trackerManager.mdList.logout()

        return@withIOContext true
    }
}
