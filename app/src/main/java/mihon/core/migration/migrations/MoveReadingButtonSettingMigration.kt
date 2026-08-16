package mihon.core.migration.migrations

import android.app.Application
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
class MoveReadingButtonSettingMigration(
    private val context: Application,
    private val libraryPreferences: LibraryPreferences,
) : Migration {
    override val version: Float = 43f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getBoolean("start_reading_button", false)) {
            libraryPreferences.showContinueReadingButton.set(true)
        }

        return@withIOContext true
    }
}
