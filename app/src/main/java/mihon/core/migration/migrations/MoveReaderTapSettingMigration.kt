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
class MoveReaderTapSettingMigration(
    private val context: Application,
    private val readerPreferences: ReaderPreferences,
) : Migration {
    override val version: Float = 32f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val oldReaderTap = prefs.getBoolean("reader_tap", false)
        if (!oldReaderTap) {
            readerPreferences.navigationModePager.set(5)
            readerPreferences.navigationModeWebtoon.set(5)
        }

        return@withIOContext true
    }
}
