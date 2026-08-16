package mihon.core.migration.migrations

import android.app.Application
import androidx.preference.PreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.minusAssign
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.library.service.LibraryPreferences

@Inject
@ContributesIntoSet(AppScope::class)
class MoveLibraryNonCompleteSettingMigration(
    private val context: Application,
    private val libraryPreferences: LibraryPreferences,
) : Migration {
    override val version: Float = 23f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val oldUpdateOngoingOnly = prefs.getBoolean("pref_update_only_non_completed_key", true)
        if (!oldUpdateOngoingOnly) {
            libraryPreferences.autoUpdateMangaRestrictions -= LibraryPreferences.MANGA_NON_COMPLETED
        }

        return@withIOContext true
    }
}
