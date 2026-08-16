package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.library.service.LibraryPreferences

@Inject
@ContributesIntoSet(AppScope::class)
class RemoveBatteryNotLowRestrictionMigration(
    private val libraryPreferences: LibraryPreferences,
) : Migration {
    override val version: Float = 56f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val pref = libraryPreferences.autoUpdateDeviceRestrictions
        if (pref.isSet() && "battery_not_low" in pref.get()) {
            pref.getAndSet { it - "battery_not_low" }
        }

        return@withIOContext true
    }
}
