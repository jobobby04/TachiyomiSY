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
class LogoutFromMALMigration(
    private val trackerManager: TrackerManager,
) : Migration {
    override val version: Float = 12f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        // Force MAL log out due to login flow change
        trackerManager.myAnimeList?.logout()

        return@withIOContext true
    }
}
