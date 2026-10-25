package mihon.core.migration.migrations

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.cache.PagePreviewCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.system.logcat
import java.io.File

/**
 * SY-specific file
 */
@Inject
@ContributesIntoSet(AppScope::class)
class ClearBrokenPagePreviewCacheMigration(
    private val context: Context,
    private val pagePreviewCache: PagePreviewCache,
) : Migration {
    override val version: Float = 58f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        pagePreviewCache.clear()
        File(context.cacheDir, PagePreviewCache.PARAMETER_CACHE_DIRECTORY).listFiles()?.forEach {
            if (it.name == "journal" || it.name.startsWith("journal.")) {
                return@forEach
            }

            try {
                it.delete()
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to remove file from cache" }
            }
        }

        return@withContext true
    }
}
