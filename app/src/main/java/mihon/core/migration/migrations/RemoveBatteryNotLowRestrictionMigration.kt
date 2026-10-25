package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.domain.library.service.LibraryPreferences

/**
 * SY-specific file
 */
@Inject
@ContributesIntoSet(AppScope::class)
class RemoveBatteryNotLowRestrictionMigration(
    private val libraryPreferences: LibraryPreferences,
) : Migration {
    override val version: Float = 56f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        val pref = libraryPreferences.autoUpdateDeviceRestrictions
        if (pref.isSet() && "battery_not_low" in pref.get()) {
            pref.getAndSet { it - "battery_not_low" }
        }

        return@withContext true
    }
}
