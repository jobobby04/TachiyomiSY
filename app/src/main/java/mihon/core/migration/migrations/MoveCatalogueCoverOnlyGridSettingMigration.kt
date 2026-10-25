package mihon.core.migration.migrations

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

/**
 * SY-specific file
 */
@Inject
@ContributesIntoSet(AppScope::class)
class MoveCatalogueCoverOnlyGridSettingMigration(
    private val context: Context,
) : Migration {
    override val version: Float = 29f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getString("pref_display_mode_catalogue", null) == "NO_TITLE_GRID") {
            prefs.edit(commit = true) {
                putString("pref_display_mode_catalogue", "COMPACT_GRID")
            }
        }

        return@withContext true
    }
}
