package mihon.core.migration.migrations

import android.content.Context
import androidx.preference.PreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.domain.library.service.LibraryPreferences

/**
 * SY-specific file
 */
@Inject
@ContributesIntoSet(AppScope::class)
class MoveReadingButtonSettingMigration(
    private val context: Context,
    private val libraryPreferences: LibraryPreferences,
) : Migration {
    override val version: Float = 43f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getBoolean("start_reading_button", false)) {
            libraryPreferences.showContinueReadingButton.set(true)
        }

        return@withContext true
    }
}
