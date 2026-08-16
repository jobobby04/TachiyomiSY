package mihon.core.migration.migrations

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

@Inject
@ContributesIntoSet(AppScope::class)
class ChangeTrackingQueueTypeMigration(
    private val context: Application,
) : Migration {
    override val version: Float = 44f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val trackingQueuePref = context.getSharedPreferences("tracking_queue", Context.MODE_PRIVATE)
        trackingQueuePref.all.forEach {
            val (_, lastChapterRead) = it.value.toString().split(":")
            trackingQueuePref.edit {
                remove(it.key)
                putFloat(it.key, lastChapterRead.toFloat())
            }
        }

        return@withIOContext true
    }
}
