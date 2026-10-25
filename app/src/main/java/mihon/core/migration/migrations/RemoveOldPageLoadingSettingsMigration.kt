package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore

/**
 * SY-specific file
 * The reader loads pages in parallel now, so SY's old page loading settings no longer do anything.
 */
@Inject
@ContributesIntoSet(AppScope::class)
class RemoveOldPageLoadingSettingsMigration(
    private val preferenceStore: PreferenceStore,
) : Migration {
    override val version: Float = 84f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        listOf(
            preferenceStore.getInt("eh_reader_threads"),
            preferenceStore.getBoolean("eh_reader_instant_retry"),
            preferenceStore.getBoolean("eh_aggressive_page_loading"),
            preferenceStore.getInt("eh_preload_size"),
        ).forEach { it.delete() }
        return true
    }
}
