package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.domain.ui.UiPreferences
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext

@Inject
@ContributesIntoSet(AppScope::class)
class MoveRelativeTimeSettingMigration(
    private val preferenceStore: PreferenceStore,
    private val uiPreferences: UiPreferences,
) : Migration {
    override val version: Float = 57f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val pref = preferenceStore.getInt("relative_time", 7)
        if (pref.get() == 0) {
            uiPreferences.relativeTime.set(false)
        }

        return@withIOContext true
    }
}
