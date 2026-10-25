package mihon.core.migration.migrations

import android.content.Context
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
class MoveCacheToDiskSettingMigration(
    private val context: Context,
    private val readerPreferences: ReaderPreferences,
) : Migration {
    override val version: Float = 66f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val cacheImagesToDisk = prefs.getBoolean("cache_archive_manga_on_disk", false)
        if (cacheImagesToDisk) {
            readerPreferences.archiveReaderMode.set(ReaderPreferences.ArchiveReaderMode.CACHE_TO_DISK)
        }

        return@withContext true
    }
}
