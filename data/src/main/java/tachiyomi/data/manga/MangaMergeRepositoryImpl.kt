package tachiyomi.data.manga

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.data.subscribeToOneOrNull
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergeSettings
import tachiyomi.domain.manga.model.MergedEntry
import tachiyomi.domain.manga.repository.MangaMergeRepository

/**
 * SY-specific file
 */
class MangaMergeRepositoryImpl(
    private val database: Database,
) : MangaMergeRepository {

    override suspend fun getMergeSettings(mergeId: Long): MergeSettings? {
        return database.mergedQueries
            .selectMerge(mangaId = mergeId, mapper = MergedMangaMapper::mapMergeSettings)
            .awaitAsOneOrNull()
    }

    override suspend fun subscribeMergeSettings(mergeId: Long): Flow<MergeSettings?> {
        return database.mergedQueries
            .selectMerge(mangaId = mergeId, mapper = MergedMangaMapper::mapMergeSettings)
            .subscribeToOneOrNull()
    }

    override suspend fun updateMergeSettings(mergeId: Long, chapterSortMode: Int, infoMangaId: Long?): Boolean {
        return try {
            database.mergedQueries.updateMerge(
                mangaId = mergeId,
                chapterSortMode = chapterSortMode.toLong(),
                infoMangaId = infoMangaId,
            )
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun insertMerge(mangaId: Long, chapterSortMode: Int, infoMangaId: Long?) {
        database.mergedQueries.insertMerge(
            mangaId = mangaId,
            chapterSortMode = chapterSortMode.toLong(),
            infoMangaId = infoMangaId,
        )
    }

    override suspend fun getEntriesById(id: Long): List<MergedEntry> {
        return database.mergedQueries
            .selectEntriesByMergeId(mergeId = id, mapper = MergedMangaMapper::mapEntry)
            .awaitAsList()
    }

    override suspend fun subscribeEntriesById(id: Long): Flow<List<MergedEntry>> {
        return database.mergedQueries
            .selectEntriesByMergeId(mergeId = id, mapper = MergedMangaMapper::mapEntry)
            .subscribeToList()
    }

    override suspend fun insert(entry: MergedEntry): Long? {
        return database.transactionWithResult {
            database.mergedQueries
                .insertEntryReturningId(
                    mergeId = entry.mergeId,
                    mangaId = entry.mangaId,
                    fetchChapterUpdates = entry.fetchChapterUpdates,
                    downloadChapters = entry.downloadChapters,
                    chapterPriority = entry.chapterPriority.toLong(),
                )
                .awaitAsOneOrNull()
        }
    }

    override suspend fun insertAll(entries: List<MergedEntry>) {
        database.transaction {
            entries.forEach { entry ->
                database.mergedQueries.insertEntry(
                    mergeId = entry.mergeId,
                    mangaId = entry.mangaId,
                    fetchChapterUpdates = entry.fetchChapterUpdates,
                    downloadChapters = entry.downloadChapters,
                    chapterPriority = entry.chapterPriority.toLong(),
                )
            }
        }
    }

    override suspend fun updateAllEntries(entries: List<MergedEntry>): Boolean {
        return try {
            database.transaction {
                entries.forEach { entry ->
                    database.mergedQueries.updateEntry(
                        id = entry.id,
                        fetchChapterUpdates = entry.fetchChapterUpdates,
                        downloadChapters = entry.downloadChapters,
                        chapterPriority = entry.chapterPriority.toLong(),
                    )
                }
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun deleteById(id: Long) {
        database.mergedQueries.deleteEntryById(id = id)
    }

    override suspend fun deleteByMergeId(mergeId: Long) {
        database.mergedQueries.deleteEntriesByMergeId(mergeId = mergeId)
    }

    override suspend fun getMergedManga(): List<Manga> {
        return database.mergedQueries
            .selectAllMergedMangas(MangaMapper::mapManga)
            .awaitAsList()
    }

    override suspend fun subscribeMergedManga(): Flow<List<Manga>> {
        return database.mergedQueries
            .selectAllMergedMangas(MangaMapper::mapManga)
            .subscribeToList()
    }

    override suspend fun getMergedMangaById(id: Long): List<Manga> {
        return database.mergedQueries
            .selectMergedMangasById(mergeId = id, mapper = MangaMapper::mapManga)
            .awaitAsList()
    }

    override suspend fun subscribeMergedMangaById(id: Long): Flow<List<Manga>> {
        return database.mergedQueries
            .selectMergedMangasById(mergeId = id, mapper = MangaMapper::mapManga)
            .subscribeToList()
    }

    override suspend fun getMergeMangaForDownloading(mergeId: Long): List<Manga> {
        return database.mergedQueries
            .selectMergedMangasForDownloadingById(mergeId = mergeId, mapper = MangaMapper::mapManga)
            .awaitAsList()
    }

    override suspend fun getAllEntries(): List<MergedEntry> {
        return database.mergedQueries
            .selectAllEntries(MergedMangaMapper::mapEntry)
            .awaitAsList()
    }

    override suspend fun applyMerge(
        staleMangaId: Long?,
        mergedManga: Manga,
        categoryIds: List<Long>,
        chapterSortMode: Int,
        infoMangaId: Long?,
        entries: List<MergedEntry>,
    ): Manga {
        return database.transactionWithResult {
            if (staleMangaId != null) {
                database.mergedQueries.deleteEntriesByMergeId(mergeId = staleMangaId)
                database.mangaQueries.deleteById(id = staleMangaId)
            }

            val inserted = database.mangaQueries.insertNetworkManga(
                sourceId = mergedManga.source,
                remoteUrl = mergedManga.url,
                remoteArtist = mergedManga.ogArtist,
                remoteAuthor = mergedManga.ogAuthor,
                remoteDescription = mergedManga.ogDescription,
                remoteGenre = mergedManga.ogGenre,
                remoteTitle = mergedManga.ogTitle,
                remoteStatus = mergedManga.ogStatus,
                remoteCover = mergedManga.ogThumbnailUrl,
                userFavoriteAt = mergedManga.favoriteAt,
                stateChapterLastUpdate = mergedManga.lastUpdate,
                stateChapterNextUpdate = mergedManga.nextUpdate,
                stateChapterFetchInterval = mergedManga.fetchInterval.toLong(),
                stateInitialized = mergedManga.initialized,
                userReaderFlags = mergedManga.viewerFlags,
                userChapterFlags = mergedManga.chapterFlags,
                stateCoverLastModified = mergedManga.coverLastModified,
                remoteUpdateStrategy = mergedManga.updateStrategy,
                remoteMemo = mergedManga.memo,
                stateVersion = mergedManga.version,
                updateTitle = mergedManga.ogTitle.isNotBlank(),
                updateCover = !mergedManga.ogThumbnailUrl.isNullOrBlank(),
                updateDetails = mergedManga.initialized,
                mapper = MangaMapper::mapManga,
            ).awaitAsOne()

            database.manga_categoryQueries.deleteMangaCategoryByMangaId(inserted.id)
            categoryIds.forEach { categoryId ->
                database.manga_categoryQueries.insert(mangaId = inserted.id, categoryId = categoryId)
            }

            // Create the merge-level settings row only for a fresh merge; preserve it when reusing
            if (database.mergedQueries.selectMerge(mangaId = inserted.id).awaitAsOneOrNull() == null) {
                database.mergedQueries.insertMerge(
                    mangaId = inserted.id,
                    chapterSortMode = chapterSortMode.toLong(),
                    infoMangaId = infoMangaId,
                )
            }

            entries.forEach { entry ->
                database.mergedQueries.insertEntry(
                    mergeId = inserted.id,
                    mangaId = entry.mangaId,
                    fetchChapterUpdates = entry.fetchChapterUpdates,
                    downloadChapters = entry.downloadChapters,
                    chapterPriority = entry.chapterPriority.toLong(),
                )
            }

            inserted
        }
    }
}
