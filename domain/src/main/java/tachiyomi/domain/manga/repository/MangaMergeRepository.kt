package tachiyomi.domain.manga.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergeSettings
import tachiyomi.domain.manga.model.MergedEntry

/**
 * SY-specific file
 */
interface MangaMergeRepository {
    suspend fun getMergeSettings(mergeId: Long): MergeSettings?

    suspend fun subscribeMergeSettings(mergeId: Long): Flow<MergeSettings?>

    suspend fun updateMergeSettings(mergeId: Long, chapterSortMode: Int, infoMangaId: Long?): Boolean

    suspend fun insertMerge(mangaId: Long, chapterSortMode: Int, infoMangaId: Long?)

    suspend fun getEntriesById(id: Long): List<MergedEntry>

    suspend fun subscribeEntriesById(id: Long): Flow<List<MergedEntry>>

    suspend fun insert(entry: MergedEntry): Long?

    suspend fun insertAll(entries: List<MergedEntry>)

    suspend fun updateAllEntries(entries: List<MergedEntry>): Boolean

    suspend fun deleteById(id: Long)

    suspend fun deleteByMergeId(mergeId: Long)

    suspend fun getMergedManga(): List<Manga>

    suspend fun subscribeMergedManga(): Flow<List<Manga>>

    suspend fun getMergedMangaById(id: Long): List<Manga>

    suspend fun subscribeMergedMangaById(id: Long): Flow<List<Manga>>

    suspend fun getMergeMangaForDownloading(mergeId: Long): List<Manga>

    suspend fun getAllEntries(): List<MergedEntry>

    suspend fun applyMerge(
        staleMangaId: Long?,
        mergedManga: Manga,
        categoryIds: List<Long>,
        chapterSortMode: Int,
        infoMangaId: Long?,
        entries: List<MergedEntry>,
    ): Manga
}
