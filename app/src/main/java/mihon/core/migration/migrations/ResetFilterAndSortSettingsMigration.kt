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
import tachiyomi.domain.library.service.LibraryPreferences

/**
 * SY-specific file
 */
@Inject
@ContributesIntoSet(AppScope::class)
class ResetFilterAndSortSettingsMigration(
    private val context: Context,
    private val libraryPreferences: LibraryPreferences,
) : Migration {
    override val version: Float = 41f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val preferences = listOf(
            libraryPreferences.filterChapterByRead,
            libraryPreferences.filterChapterByDownloaded,
            libraryPreferences.filterChapterByBookmarked,
            libraryPreferences.sortChapterBySourceOrNumber,
            libraryPreferences.displayChapterByNameOrNumber,
            libraryPreferences.sortChapterByAscendingOrDescending,
        )

        prefs.edit {
            preferences.forEach { preference ->
                val key = preference.key()
                val value = prefs.getInt(key, Int.MIN_VALUE)
                if (value == Int.MIN_VALUE) return@forEach
                remove(key)
                putLong(key, value.toLong())
            }
        }

        return@withContext true
    }
}
