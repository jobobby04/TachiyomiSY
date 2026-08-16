package mihon.core.migration.migrations

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import exh.eh.EHentaiUpdateWorker
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

@Inject
@ContributesIntoSet(AppScope::class)
class SetupEHentaiUpdateMigration(
    private val context: Application,
) : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        EHentaiUpdateWorker.scheduleBackground(context)
        return true
    }
}
