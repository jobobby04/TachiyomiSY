package mihon.core.migration.migrations

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

/**
 * SY-specific file
 */
@Inject
@ContributesIntoSet(AppScope::class)
class MoveVerticalSeekbarSettingsMigration(
    private val context: Context,
    private val readerPreferences: ReaderPreferences,
) : Migration {
    override val version: Float = 77f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit {
            val forceHorzSeekbar = prefs.getBoolean("pref_force_horz_seekbar", false)
            if (forceHorzSeekbar) {
                putBoolean(
                    "pref_webtoon_vertical_navigator",
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

        return@withContext true
    }
}
