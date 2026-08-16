package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

@Inject
@ContributesIntoSet(AppScope::class)
class RemoveOldReaderThemeMigration(
    private val readerPreferences: ReaderPreferences,
) : Migration {
    override val version: Float = 18f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val readerTheme = readerPreferences.readerTheme.get()
        if (readerTheme == 4) {
            readerPreferences.readerTheme.set(3)
        }

        return@withIOContext true
    }
}
