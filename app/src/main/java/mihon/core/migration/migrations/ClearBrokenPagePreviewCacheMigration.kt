package mihon.core.migration.migrations

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.cache.PagePreviewCache
import logcat.LogPriority
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import java.io.File

@Inject
@ContributesIntoSet(AppScope::class)
class ClearBrokenPagePreviewCacheMigration(
    private val context: Application,
    private val pagePreviewCache: PagePreviewCache,
) : Migration {
    override val version: Float = 58f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
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

        return@withIOContext true
    }
}
