package mihon.core.migration.migrations

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.library.service.LibraryPreferences

@Inject
@ContributesIntoSet(AppScope::class)
class MoveSortingModeSettingMigration(
    private val context: Application,
    private val libraryPreferences: LibraryPreferences,
) : Migration {
    override val version: Float = 39f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit {
            val sort = prefs.getString(libraryPreferences.sortingMode.key(), null) ?: return@edit
            val direction = prefs.getString("library_sorting_ascending", "ASCENDING")!!
            putString(libraryPreferences.sortingMode.key(), "$sort,$direction")
            remove("library_sorting_ascending")
        }

        return@withIOContext true
    }
}
