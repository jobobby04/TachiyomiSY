package mihon.core.migration.migrations

import android.content.SharedPreferences
import androidx.core.content.edit
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

class RemoveDuplicateReaderPreferenceMigration : Migration {
    override val version: Float = 74f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        migrationContext.get<SharedPreferences>()?.edit {
            remove("mark_read_dupe")
        }

        return@withIOContext true
    }
}
