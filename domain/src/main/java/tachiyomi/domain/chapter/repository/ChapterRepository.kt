package tachiyomi.domain.chapter.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterRemoteUpdate
import tachiyomi.domain.chapter.model.ChapterSummary
import tachiyomi.domain.chapter.model.ChapterUpdate

interface ChapterRepository {

    suspend fun update(chapterUpdate: ChapterUpdate)

    suspend fun updateAll(chapterUpdates: List<ChapterUpdate>)

    suspend fun updateFromRemote(
        removedIds: List<Long>,
        added: List<Chapter>,
        updated: List<ChapterRemoteUpdate>,
    ): List<Chapter>

    suspend fun getChapterByMangaId(mangaId: Long, applyScanlatorFilter: Boolean = false): List<Chapter>

    // SY -->
    suspend fun addAll(chapters: List<Chapter>): List<Chapter>

    suspend fun removeChaptersWithIds(ids: List<Long>)

    suspend fun getChaptersByMangaIds(mangaIds: List<Long>): List<Chapter>
    // SY <--

    suspend fun getChapterSummaryByMangaId(mangaId: Long): ChapterSummary

    suspend fun getScanlatorsByMangaId(mangaId: Long): List<String>

    fun getScanlatorsByMangaIdAsFlow(mangaId: Long): Flow<List<String>>

    suspend fun getBookmarkedChaptersByMangaId(mangaId: Long): List<Chapter>

    suspend fun getChapterById(id: Long): Chapter?

    suspend fun getChapterByMangaIdAsFlow(mangaId: Long, applyScanlatorFilter: Boolean = false): Flow<List<Chapter>>

    suspend fun getChapterByUrlAndMangaId(url: String, mangaId: Long): Chapter?

    // SY -->
    suspend fun getChapterByUrl(url: String): List<Chapter>

    suspend fun getDedupedMergedChapters(
        mangaId: Long,
        sortMode: Int,
        applyScanlatorFilter: Boolean = false,
    ): List<Chapter>

    suspend fun getDedupedMergedChaptersAsFlow(
        mangaId: Long,
        sortMode: Int,
        applyScanlatorFilter: Boolean = false,
    ): Flow<List<Chapter>>

    suspend fun getScanlatorsByMergeId(mangaId: Long): List<String>

    fun getScanlatorsByMergeIdAsFlow(mangaId: Long): Flow<List<String>>

    suspend fun resetIsSyncing()
    // SY <--
}
