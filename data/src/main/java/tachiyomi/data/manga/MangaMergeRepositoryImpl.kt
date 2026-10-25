package tachiyomi.data.manga

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergeMangaSettingsUpdate
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.manga.repository.MangaMergeRepository

/**
 * SY-specific file
 */
class MangaMergeRepositoryImpl(
    private val database: Database,
) : MangaMergeRepository {

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

    override suspend fun getReferencesById(id: Long): List<MergedMangaReference> {
        return database.mergedQueries
            .selectByMergeId(mergeId = id, mapper = MergedMangaMapper::map)
            .awaitAsList()
    }

    override suspend fun subscribeReferencesById(id: Long): Flow<List<MergedMangaReference>> {
        return database.mergedQueries
            .selectByMergeId(mergeId = id, mapper = MergedMangaMapper::map)
            .subscribeToList()
    }

    override suspend fun updateSettings(update: MergeMangaSettingsUpdate): Boolean {
        return try {
            partialUpdate(update)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun updateAllSettings(values: List<MergeMangaSettingsUpdate>): Boolean {
        return try {
            partialUpdate(*values.toTypedArray())
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    private suspend fun partialUpdate(vararg values: MergeMangaSettingsUpdate) {
        database.transaction {
            values.forEach { value ->
                database.mergedQueries.updateSettingsById(
                    id = value.id,
                    getChapterUpdates = value.getChapterUpdates,
                    downloadChapters = value.downloadChapters,
                    infoManga = value.isInfoManga,
                    chapterPriority = value.chapterPriority?.toLong(),
                    chapterSortMode = value.chapterSortMode?.toLong(),
                )
            }
        }
    }

    override suspend fun insert(reference: MergedMangaReference): Long? {
        return database.transactionWithResult {
            database.mergedQueries
                .insertReturningId(
                    infoManga = reference.isInfoManga,
                    getChapterUpdates = reference.getChapterUpdates,
                    chapterSortMode = reference.chapterSortMode.toLong(),
                    chapterPriority = reference.chapterPriority.toLong(),
                    downloadChapters = reference.downloadChapters,
                    mergeId = reference.mergeId!!,
                    mergeUrl = reference.mergeUrl,
                    mangaId = reference.mangaId,
                    mangaUrl = reference.mangaUrl,
                    mangaSource = reference.mangaSourceId,
                )
                .awaitAsOneOrNull()
        }
    }

    override suspend fun insertAll(references: List<MergedMangaReference>) {
        database.transaction {
            references.forEach { reference ->
                database.mergedQueries.insert(
                    infoManga = reference.isInfoManga,
                    getChapterUpdates = reference.getChapterUpdates,
                    chapterSortMode = reference.chapterSortMode.toLong(),
                    chapterPriority = reference.chapterPriority.toLong(),
                    downloadChapters = reference.downloadChapters,
                    mergeId = reference.mergeId!!,
                    mergeUrl = reference.mergeUrl,
                    mangaId = reference.mangaId,
                    mangaUrl = reference.mangaUrl,
                    mangaSource = reference.mangaSourceId,
                )
            }
        }
    }

    override suspend fun applyMerge(
        staleMangaId: Long?,
        mergedManga: Manga,
        categoryIds: List<Long>,
        references: List<MergedMangaReference>,
    ): Manga {
        return database.transactionWithResult {
            if (staleMangaId != null) {
                database.mergedQueries.deleteByMergeId(mergeId = staleMangaId)
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

            references.forEach { reference ->
                val mangaId = if (reference.mangaSourceId == MERGED_SOURCE_ID) inserted.id else reference.mangaId
                database.mergedQueries.insert(
                    infoManga = reference.isInfoManga,
                    getChapterUpdates = reference.getChapterUpdates,
                    chapterSortMode = reference.chapterSortMode.toLong(),
                    chapterPriority = reference.chapterPriority.toLong(),
                    downloadChapters = reference.downloadChapters,
                    mergeId = inserted.id,
                    mergeUrl = reference.mergeUrl,
                    mangaId = mangaId,
                    mangaUrl = reference.mangaUrl,
                    mangaSource = reference.mangaSourceId,
                )
            }

            inserted
        }
    }

    override suspend fun deleteById(id: Long) {
        database.mergedQueries
            .deleteById(id = id)
    }

    override suspend fun deleteByMergeId(mergeId: Long) {
        database.mergedQueries
            .deleteByMergeId(mergeId = mergeId)
    }

    override suspend fun getMergeMangaForDownloading(mergeId: Long): List<Manga> {
        return database.mergedQueries
            .selectMergedMangasForDownloadingById(mergeId = mergeId, mapper = MangaMapper::mapManga)
            .awaitAsList()
    }

    override suspend fun getAllReferences(): List<MergedMangaReference> {
        return database.mergedQueries
            .selectAll(MergedMangaMapper::map)
            .awaitAsList()
    }
}
