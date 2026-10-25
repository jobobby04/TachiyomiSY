package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.domain.ui.UiPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore

/**
 * SY-specific file
 */
@Inject
@ContributesIntoSet(AppScope::class)
class MoveRelativeTimeSettingMigration(
    private val preferenceStore: PreferenceStore,
    private val uiPreferences: UiPreferences,
) : Migration {
    override val version: Float = 57f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        val pref = preferenceStore.getInt("relative_time", 7)
        if (pref.get() == 0) {
            uiPreferences.relativeTime.set(false)
        }

        return@withContext true
    }
}
