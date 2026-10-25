package tachiyomi.domain.eh.repository

/**
 * SY-specific file
 */
interface EhRepository {

    suspend fun addAllMangaInDatabaseToLibrary()

    suspend fun migrateSource(newId: Long, oldId: Long)

    suspend fun fixReaderViewerBackupBug()

    suspend fun resetReaderViewerForAllManga()

    suspend fun migrateAllNhentaiToOtherLang(otherId: Long, sources: Set<Long>)

    suspend fun deleteByTrackerId(trackerId: Long)
}
