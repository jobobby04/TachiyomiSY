package tachiyomi.domain.manga.service

import tachiyomi.domain.manga.model.MangaTombstoneKey

/**
 * SY-specific file
 *
 * Records manga deleted locally so the sync client can tombstone them on its next request.
 */
fun interface MangaTombstones {
    suspend fun remember(mangas: Collection<MangaTombstoneKey>)
}
