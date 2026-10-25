package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.source.online.all.NHentai
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.core.migration.MigrateUtils
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.domain.eh.repository.EhRepository

/**
 * SY-specific file
 */
@Inject
@ContributesIntoSet(AppScope::class)
class DelegateNHentaiMigration(
    private val ehRepository: EhRepository,
) : Migration {
    override val version: Float = 6f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        MigrateUtils.updateSourceId(ehRepository, NHentai.otherId, 6907)

        return@withContext true
    }
}
