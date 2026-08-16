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

@Inject
@ContributesIntoSet(AppScope::class)
class ResetRotationSettingMigration(
    private val context: Application,
) : Migration {
    override val version: Float = 16f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        // Reset rotation to Free after replacing Lock
        if (prefs.contains("pref_rotation_type_key")) {
            prefs.edit {
                putInt("pref_rotation_type_key", 1)
            }
        }

        return@withIOContext true
    }
}
