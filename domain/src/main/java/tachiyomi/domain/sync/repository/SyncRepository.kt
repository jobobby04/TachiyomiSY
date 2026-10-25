package tachiyomi.domain.sync.repository

/**
 * SY-specific file
 */
interface SyncRepository {

    suspend fun resetAllSyncing()

    suspend fun resetMangaSyncing()
}
