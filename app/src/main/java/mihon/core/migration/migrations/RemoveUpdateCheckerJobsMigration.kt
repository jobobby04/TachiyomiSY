package mihon.core.migration.migrations

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getEnum

/**
 * SY-specific file
 */
@Inject
@ContributesIntoSet(AppScope::class)
class RemoveUpdateCheckerJobsMigration(
    private val context: Context,
    private val preferenceStore: PreferenceStore,
    private val trackerManager: TrackerManager,
) : Migration {
    override val version: Float = 52f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        // Removed background jobs
        context.workManager.cancelAllWorkByTag("UpdateChecker")
        context.workManager.cancelAllWorkByTag("ExtensionUpdate")
        prefs.edit {
            remove("automatic_ext_updates")
        }
        val prefKeys = listOf(
            "pref_filter_library_downloaded",
            "pref_filter_library_unread",
            "pref_filter_library_started",
            "pref_filter_library_bookmarked",
            "pref_filter_library_completed",
            "pref_filter_library_lewd",
        ) + trackerManager.trackers.map { "pref_filter_library_tracked_${it.id}" }

        prefKeys.forEach { key ->
            val pref = prefs.getInt(key, 0)
            prefs.edit {
                remove(key)

                val newValue = when (pref) {
                    1 -> TriState.ENABLED_IS
                    2 -> TriState.ENABLED_NOT
                    else -> TriState.DISABLED
                }

                preferenceStore.getEnum("${key}_v2", TriState.DISABLED).set(newValue)
            }
        }

        return@withContext true
    }
}
