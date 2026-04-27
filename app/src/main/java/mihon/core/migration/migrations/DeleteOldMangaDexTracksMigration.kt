package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.data.Database

class DeleteOldMangaDexTracksMigration : Migration {
    override val version: Float = 17f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val database = migrationContext.get<Database>() ?: return@withIOContext false
        // Delete old mangadex trackers
        database.ehQueries.deleteBySyncId(6)
        return@withIOContext true
    }
}
