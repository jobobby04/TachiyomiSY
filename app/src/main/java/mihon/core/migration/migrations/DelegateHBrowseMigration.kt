package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.domain.manga.interactor.UpdateManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.core.migration.MigrateUtils
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.domain.eh.repository.EhRepository
import tachiyomi.domain.manga.interactor.GetMangaBySource
import tachiyomi.domain.manga.model.MangaUpdate

/**
 * SY-specific file
 */
@Inject
@ContributesIntoSet(AppScope::class)
class DelegateHBrowseMigration(
    private val ehRepository: EhRepository,
    private val getMangaBySource: GetMangaBySource,
    private val updateManga: UpdateManga,
) : Migration {
    override val version: Float = 4f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        MigrateUtils.updateSourceId(ehRepository, 1401584337232758222, 6912)

        // Migrate BHrowse URLs
        val hBrowseManga = getMangaBySource.await(1401584337232758222)
        val mangaUpdates = hBrowseManga.map {
            MangaUpdate(it.id) { url = it.url + "/c00001/" }
        }
        updateManga.awaitAll(mangaUpdates)
        return@withContext true
    }
}
