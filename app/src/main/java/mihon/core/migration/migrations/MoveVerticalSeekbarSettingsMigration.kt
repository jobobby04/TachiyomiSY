package mihon.core.migration.migrations

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

class MoveVerticalSeekbarSettingsMigration : Migration {
    override val version: Float = 77f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val context = migrationContext.get<Application>() ?: return@withIOContext false
        val readerPreferences = migrationContext.get<ReaderPreferences>() ?: return@withIOContext false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit {
            val forceHorzSeekbar = prefs.getBoolean("pref_force_horz_seekbar", false)
            if (forceHorzSeekbar) {
                putBoolean(
                    readerPreferences.verticalNavigatorForLongStrip.key(),
                    false,
                )
            }
            remove("pref_force_horz_seekbar")
            val leftVerticalSeekbar = prefs.getBoolean("pref_left_handed_vertical_seekbar", false)
            if (leftVerticalSeekbar) {
                putBoolean(
                    readerPreferences.verticalNavigatorOnLeft.key(),
                    true,
                )
            }
            remove("pref_left_handed_vertical_seekbar")
        }

        return@withIOContext true
    }
}
