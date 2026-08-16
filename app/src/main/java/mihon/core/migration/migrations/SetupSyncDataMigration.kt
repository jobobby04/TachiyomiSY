package mihon.core.migration.migrations

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

@Inject
@ContributesIntoSet(AppScope::class)
class SetupSyncDataMigration(
    private val context: Application,
) : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        SyncDataJob.setupTask(context)
        return true
    }
}
