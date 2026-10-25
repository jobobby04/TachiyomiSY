package mihon.core.migration.migrations

import android.content.Context
import androidx.core.content.edit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

/**
 * SY-specific file
 */
@Inject
@ContributesIntoSet(AppScope::class)
class ChangeTrackingQueueTypeMigration(
    private val context: Context,
) : Migration {
    override val version: Float = 44f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        val trackingQueuePref = context.getSharedPreferences("tracking_queue", Context.MODE_PRIVATE)
        trackingQueuePref.all.forEach {
            val (_, lastChapterRead) = it.value.toString().split(":")
            trackingQueuePref.edit {
                remove(it.key)
                putFloat(it.key, lastChapterRead.toFloat())
            }
        }

        return@withContext true
    }
}
