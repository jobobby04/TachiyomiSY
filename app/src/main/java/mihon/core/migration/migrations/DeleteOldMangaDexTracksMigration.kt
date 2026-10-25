package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.domain.eh.repository.EhRepository

/**
 * SY-specific file
 */
@Inject
@ContributesIntoSet(AppScope::class)
class DeleteOldMangaDexTracksMigration(
    private val ehRepository: EhRepository,
) : Migration {
    override val version: Float = 17f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        // Delete old mangadex trackers
        ehRepository.deleteByTrackerId(6)
        return@withContext true
    }
}
