package mihon.core.migration.migrations

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
class RemoveOldReaderThemeMigration(
    private val readerPreferences: ReaderPreferences,
) : Migration {
    override val version: Float = 18f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        val readerTheme = readerPreferences.readerTheme.get()
        if (readerTheme == 4) {
            readerPreferences.readerTheme.set(3)
        }

        return@withContext true
    }
}
