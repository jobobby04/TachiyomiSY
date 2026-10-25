package tachiyomi.data.sync

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.data.Database
import tachiyomi.domain.sync.repository.SyncRepository

/**
 * SY-specific file
 */
@Inject
@ContributesBinding(AppScope::class)
class SyncRepositoryImpl(
    private val database: Database,
) : SyncRepository {

    override suspend fun resetAllSyncing() {
        database.transaction {
            database.mangaQueries.resetIsSyncing()
            database.chapterQueries.resetIsSyncing()
            database.categoryQueries.resetIsSyncing()
        }
    }

    override suspend fun resetMangaSyncing() {
        database.transaction {
            database.mangaQueries.resetIsSyncing()
        }
    }
}
