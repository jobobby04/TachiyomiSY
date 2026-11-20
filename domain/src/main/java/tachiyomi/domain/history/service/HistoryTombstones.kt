package tachiyomi.domain.history.service

import tachiyomi.domain.history.model.HistoryTombstoneKey

/**
 * SY-specific file
 *
 * Records history rows cleared locally so the sync client can tombstone them on its next request.
 */
fun interface HistoryTombstones {
    suspend fun remember(keys: Collection<HistoryTombstoneKey>)
}
