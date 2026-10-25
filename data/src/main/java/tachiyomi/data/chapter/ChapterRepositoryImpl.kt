package tachiyomi.data.chapter

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import logcat.LogPriority
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.chapter.ChapterMapper.mapChapter
import tachiyomi.data.subscribeToList
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterRemoteUpdate
import tachiyomi.domain.chapter.model.ChapterSummary
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import kotlin.time.Instant

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ChapterRepositoryImpl(
    private val database: Database,
) : ChapterRepository {

    // SY -->
    override suspend fun addAll(chapters: List<Chapter>): List<Chapter> {
        return try {
            database.transactionWithResult {
                chapters.mapNotNull { chapter ->
                    val chapterId = database.chapterQueries.insertReturningId(
                        mangaId = chapter.mangaId,
                        remoteUrl = chapter.url,
                        remoteName = chapter.name,
                        remoteScanlator = chapter.scanlator,
                        userRead = chapter.read,
                        userBookmark = chapter.bookmark,
                        userLastPageRead = chapter.lastPageRead,
                        remoteChapterNumber = chapter.chapterNumber,
                        remoteOrder = chapter.sourceOrder,
                        stateDateFetch = chapter.dateFetch,
                        remoteDateUpload = chapter.dateUpload,
                        remoteMemo = chapter.memo,
                        stateLastModifiedAt = chapter.lastModifiedAt,
                        stateVersion = chapter.version,
                    )
                        // A chapter already stored for the entry is skipped
                        .awaitAsOneOrNull()
                        ?: return@mapNotNull null
                    chapter.copy(id = chapterId)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    override suspend fun removeChaptersWithIds(ids: List<Long>) {
        database.chapterQueries.removeChaptersWithIds(ids)
    }
    // SY <--

    override suspend fun update(chapterUpdate: ChapterUpdate) {
        partialUpdate(chapterUpdate)
    }

    override suspend fun updateAll(chapterUpdates: List<ChapterUpdate>) {
        partialUpdate(*chapterUpdates.toTypedArray())
    }

    override suspend fun updateFromRemote(
        removedIds: List<Long>,
        added: List<Chapter>,
        updated: List<ChapterRemoteUpdate>,
    ): List<Chapter> {
        return database.transactionWithResult {
            if (removedIds.isNotEmpty()) {
                database.chapterQueries.removeChaptersWithIds(removedIds)
            }
            val existing = added.map { it.mangaId }
                .distinct()
                .flatMap { mangaId ->
                    database.chapterQueries
                        .getChaptersByMangaId(
                            mangaId = mangaId,
                            applyScanlatorFilter = false.toLong(),
                            mapper = ::mapChapter,
                        )
                        .awaitAsList()
                        .map { mangaId to it.url }
                }
                .toMutableSet()
            val stored = added.filter { existing.add(it.mangaId to it.url) }.map { chapter ->
                val chapterId = database.chapterQueries.insertReturningId(
                    mangaId = chapter.mangaId,
                    remoteUrl = chapter.url,
                    remoteName = chapter.name,
                    remoteScanlator = chapter.scanlator,
                    userRead = chapter.read,
                    userBookmark = chapter.bookmark,
                    userLastPageRead = chapter.lastPageRead,
                    remoteChapterNumber = chapter.chapterNumber,
                    remoteOrder = chapter.sourceOrder,
                    stateDateFetch = chapter.dateFetch,
                    remoteDateUpload = chapter.dateUpload,
                    remoteMemo = chapter.memo,
                    // SY -->
                    stateLastModifiedAt = chapter.lastModifiedAt,
                    stateVersion = chapter.version,
                    // SY <--
                )
                    .awaitAsOne()
                chapter.copy(id = chapterId)
            }
            updated.forEach { chapterUpdate ->
                database.chapterQueries.updateRemote(
                    remoteName = chapterUpdate.name,
                    remoteScanlator = chapterUpdate.scanlator,
                    remoteChapterNumber = chapterUpdate.chapterNumber,
                    remoteOrder = chapterUpdate.sourceOrder,
                    remoteDateUpload = chapterUpdate.dateUpload,
                    id = chapterUpdate.id,
                    remoteMemo = chapterUpdate.memo,
                )
            }
            stored
        }
    }

    private suspend fun partialUpdate(vararg chapterUpdates: ChapterUpdate) {
        database.transaction {
            chapterUpdates.forEach { chapterUpdate ->
                database.chapterQueries.update(
                    userRead = chapterUpdate.read,
                    userBookmark = chapterUpdate.bookmark,
                    userLastPageRead = chapterUpdate.lastPageRead,
                    stateDateFetch = chapterUpdate.dateFetch,
                    id = chapterUpdate.id,
                )
            }
        }
    }

    override suspend fun getChapterByMangaId(mangaId: Long, applyScanlatorFilter: Boolean): List<Chapter> {
        return database.chapterQueries
            .getChaptersByMangaId(
                mangaId = mangaId,
                applyScanlatorFilter = applyScanlatorFilter.toLong(),
                mapper = ::mapChapter,
            )
            .awaitAsList()
    }

    // SY -->
    override suspend fun getChaptersByMangaIds(mangaIds: List<Long>): List<Chapter> {
        return database.ehQueries
            .getChaptersByMangaIds(mangaIds, ::mapChapter)
            .awaitAsList()
    }
    // SY <--

    override suspend fun getChapterSummaryByMangaId(mangaId: Long): ChapterSummary {
        return database.chapterQueries
            .getChapterSummaryByMangaId(mangaId) { total, latestChapterNumber ->
                ChapterSummary(chapterCount = total, latestChapterNumber = latestChapterNumber)
            }
            .awaitAsOne()
    }

    override suspend fun getScanlatorsByMangaId(mangaId: Long): List<String> {
        return database.chapterQueries
            .getScanlatorsByMangaId(mangaId) { it.orEmpty() }
            .awaitAsList()
    }

    override fun getScanlatorsByMangaIdAsFlow(mangaId: Long): Flow<List<String>> {
        return database.chapterQueries
            .getScanlatorsByMangaId(mangaId) { it.orEmpty() }
            .subscribeToList()
    }

    override suspend fun getBookmarkedChaptersByMangaId(mangaId: Long): List<Chapter> {
        return database.chapterQueries
            .getBookmarkedChaptersByMangaId(mangaId, ::mapChapter)
            .awaitAsList()
    }

    override suspend fun getChapterById(id: Long): Chapter? {
        return database.chapterQueries
            .getChapterById(id, ::mapChapter)
            .awaitAsOneOrNull()
    }

    override suspend fun getChapterByMangaIdAsFlow(mangaId: Long, applyScanlatorFilter: Boolean): Flow<List<Chapter>> {
        return database.chapterQueries
            .getChaptersByMangaId(
                mangaId = mangaId,
                applyScanlatorFilter = applyScanlatorFilter.toLong(),
                mapper = ::mapChapter,
            )
            .subscribeToList()
    }

    override suspend fun getChapterByUrlAndMangaId(url: String, mangaId: Long): Chapter? {
        return database.chapterQueries
            .getChapterByUrlAndMangaId(mangaId = mangaId, remoteUrl = url, mapper = ::mapChapter)
            .awaitAsOneOrNull()
    }

    // SY -->
    override suspend fun getChapterByUrl(url: String): List<Chapter> {
        return database.chapterQueries
            .getChapterByUrl(url, ::mapChapter)
            .awaitAsList()
    }

    override suspend fun getDedupedMergedChapters(
        mangaId: Long,
        sortMode: Int,
        applyScanlatorFilter: Boolean,
    ): List<Chapter> {
        return database.chapterQueries
            .getDedupedMergedChapters(
                mergeId = mangaId,
                sortMode = sortMode.toLong(),
                applyScanlatorFilter = applyScanlatorFilter.toLong(),
                mapper = ::mapChapter,
            )
            .awaitAsList()
    }

    override suspend fun getDedupedMergedChaptersAsFlow(
        mangaId: Long,
        sortMode: Int,
        applyScanlatorFilter: Boolean,
    ): Flow<List<Chapter>> {
        return database.chapterQueries
            .getDedupedMergedChapters(
                mergeId = mangaId,
                sortMode = sortMode.toLong(),
                applyScanlatorFilter = applyScanlatorFilter.toLong(),
                mapper = ::mapChapter,
            )
            .subscribeToList()
    }

    override suspend fun getScanlatorsByMergeId(mangaId: Long): List<String> {
        return database.chapterQueries
            .getScanlatorsByMergeId(mergeId = mangaId) { it.orEmpty() }
            .awaitAsList()
    }

    override fun getScanlatorsByMergeIdAsFlow(mangaId: Long): Flow<List<String>> {
        return database.chapterQueries
            .getScanlatorsByMergeId(mergeId = mangaId) { it.orEmpty() }
            .subscribeToList()
    }

    override suspend fun resetIsSyncing() {
        database.chapterQueries.resetIsSyncing()
    }
    // SY <--
}
