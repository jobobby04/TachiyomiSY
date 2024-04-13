package mihon.core.migration.migrations

import eu.kanade.tachiyomi.data.track.TrackerManager
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

class LogoutFromMALMigration : Migration {
    override val version: Float = 12f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        // Force MAL log out due to login flow change
        migrationContext.get<TrackerManager>()?.myAnimeList?.logout()

        return@withIOContext true
    }
}
