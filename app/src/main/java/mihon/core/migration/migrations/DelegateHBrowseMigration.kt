package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.domain.manga.interactor.UpdateManga
import exh.source.HBROWSE_SOURCE_ID
import mihon.core.migration.MigrateUtils
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.data.Database
import tachiyomi.domain.manga.interactor.GetMangaBySource
import tachiyomi.domain.manga.model.MangaUpdate

@Inject
@ContributesIntoSet(AppScope::class)
class DelegateHBrowseMigration(
    private val database: Database,
    private val getMangaBySource: GetMangaBySource,
    private val updateManga: UpdateManga,
) : Migration {
    override val version: Float = 4f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        MigrateUtils.updateSourceId(database, HBROWSE_SOURCE_ID, 6912)

        // Migrate BHrowse URLs
        val hBrowseManga = getMangaBySource.await(HBROWSE_SOURCE_ID)
        val mangaUpdates = hBrowseManga.map {
            MangaUpdate(it.id, url = it.url + "/c00001/")
        }
        updateManga.awaitAll(mangaUpdates)
        return@withIOContext true
    }
}
