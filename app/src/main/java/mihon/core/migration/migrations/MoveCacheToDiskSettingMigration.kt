package mihon.core.migration.migrations

import android.app.Application
import androidx.preference.PreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

@Inject
@ContributesIntoSet(AppScope::class)
class MoveCacheToDiskSettingMigration(
    private val context: Application,
    private val readerPreferences: ReaderPreferences,
) : Migration {
    override val version: Float = 66f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val cacheImagesToDisk = prefs.getBoolean("cache_archive_manga_on_disk", false)
        if (cacheImagesToDisk) {
            readerPreferences.archiveReaderMode.set(ReaderPreferences.ArchiveReaderMode.CACHE_TO_DISK)
        }

        return@withIOContext true
    }
}
