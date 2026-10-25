package tachiyomi.data.eh

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import tachiyomi.data.Database
import tachiyomi.domain.eh.repository.EhRepository

/**
 * SY-specific file
 */
@Inject
@ContributesBinding(AppScope::class)
class EhRepositoryImpl(
    private val database: Database,
) : EhRepository {

    override suspend fun addAllMangaInDatabaseToLibrary() {
        database.ehQueries.addAllMangaInDatabaseToLibrary()
    }

    override suspend fun migrateSource(newId: Long, oldId: Long) {
        database.ehQueries.migrateSource(newSourceId = newId, oldSourceId = oldId)
    }

    override suspend fun fixReaderViewerBackupBug() {
        database.ehQueries.fixReaderViewerBackupBug()
    }

    override suspend fun resetReaderViewerForAllManga() {
        database.ehQueries.resetReaderViewerForAllManga()
    }

    override suspend fun migrateAllNhentaiToOtherLang(otherId: Long, sources: Set<Long>) {
        database.ehQueries.migrateAllNhentaiToOtherLang(newSourceId = otherId, sourceIds = sources)
    }

    override suspend fun deleteByTrackerId(trackerId: Long) {
        database.ehQueries.deleteByTrackerId(trackerId = trackerId)
    }
}
