package mihon.core.migration.migrations

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

@Inject
@ContributesIntoSet(AppScope::class)
class RemoveDuplicateReaderPreferenceMigration(
    private val prefs: SharedPreferences,
) : Migration {
    override val version: Float = 75f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        if (prefs.getBoolean("mark_read_dupe", false)) {
            val readPrefSet = prefs.getStringSet("mark_duplicate_read_chapter_read", emptySet())?.toMutableSet()
            readPrefSet?.add("existing")
            prefs.edit {
                putStringSet("mark_duplicate_read_chapter_read", readPrefSet)
                remove("mark_read_dupe")
            }
        }

        return@withIOContext true
    }
}
