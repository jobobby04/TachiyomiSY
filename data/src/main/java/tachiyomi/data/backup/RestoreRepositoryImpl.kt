package tachiyomi.data.backup

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject
import tachiyomi.data.Database
import tachiyomi.domain.backup.model.RestoredHistory
import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.backup.repository.RestoreRepository
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.CustomMangaRepository
import tachiyomi.domain.manga.repository.MangaMergeRepository
import tachiyomi.domain.manga.repository.MangaMetadataRepository
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.repository.TrackRepository
import kotlin.math.max
import kotlin.time.Instant

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RestoreRepositoryImpl(
    private val database: Database,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val trackRepository: TrackRepository,
    // SY -->
    private val mangaMergeRepository: MangaMergeRepository,
    private val mangaMetadataRepository: MangaMetadataRepository,
    private val customMangaRepository: CustomMangaRepository,
    // SY <--
) : RestoreRepository {

    override suspend fun getMangaUrlsBySourceId(): Map<Long, Set<String>> {
        return database.mangaQueries
            .getAllMangaSourceAndUrl()
            .awaitAsList()
            .groupBy({ it.source_id }, { it.remote_url })
            .mapValues { it.value.toSet() }
    }

    override suspend fun restoreManga(
        entries: List<RestoredManga>,
        // SY -->
        isSync: Boolean,
        // SY <--
        update: suspend (Manga) -> MangaUpdate,
    ) {
        // SY -->
        val syncingFlag = if (isSync) 1L else 0L
        // SY <--
        database.transaction {
            entries.forEach { entry ->
                // A cancelled restore stops between entries and rolls the whole batch back
                currentCoroutineContext().ensureActive()
                val manga = restore(entry, /* SY --> */ syncingFlag /* SY <-- */)
                mangaRepository.update(update(manga))
                // SY -->
                restoreMergedMangaReferences(manga.id, entry.mergedMangaReferences)
                entry.flatMetadata?.let { restoreFlatMetadata(manga.id, it) }
                entry.customMangaInfo?.let { restoreCustomMangaInfo(manga.id, it) }
                // SY <--
            }
        }
    }

    private suspend fun restore(entry: RestoredManga, /* SY --> */ syncingFlag: Long /* SY <-- */): Manga {
        val dbManga = mangaRepository.getMangaByUrlAndSourceId(entry.manga.url, entry.manga.source)
        val manga = if (dbManga == null) {
            entry.manga.copy(id = insertManga(entry.manga, /* SY --> */ syncingFlag /* SY <-- */))
        } else {
            updateManga(mergeManga(entry.manga, dbManga), /* SY --> */ syncingFlag /* SY <-- */)
        }

        if (entry.categoryIds.isNotEmpty()) {
            mangaRepository.setMangaCategories(manga.id, entry.categoryIds)
        }
        restoreChapters(manga, entry.chapters, /* SY --> */ syncingFlag /* SY <-- */)
        restoreTracking(manga, entry.tracks)
        restoreHistory(manga, entry.history)
        restoreExcludedScanlators(manga, entry.excludedScanlators)
        return manga
    }

    private fun mergeManga(manga: Manga, dbManga: Manga): Manga {
        val details = if (dbManga.initialized || !manga.initialized) dbManga else manga
        return dbManga.copy(
            favoriteAt = if (dbManga.favorite || manga.favorite) {
                listOfNotNull(dbManga.favoriteAt, manga.favoriteAt)
                    .filter { it > Manga.UNKNOWN_FAVORITE_AT }
                    .minOrNull()
                    ?: Manga.UNKNOWN_FAVORITE_AT
            } else {
                null
            },
            // SY -->
            ogTitle = details.ogTitle,
            ogArtist = details.ogArtist,
            ogAuthor = details.ogAuthor,
            ogDescription = details.ogDescription,
            ogGenre = details.ogGenre,
            ogStatus = details.ogStatus,
            ogThumbnailUrl = details.ogThumbnailUrl,
            // SY <--
            updateStrategy = details.updateStrategy,
            initialized = dbManga.initialized || manga.initialized,
            // Merge both backup and local data with local winning
            memo = JsonObject(manga.memo + dbManga.memo),
            nextUpdate = dbManga.nextUpdate ?: manga.nextUpdate,
        )
    }

    private suspend fun updateManga(manga: Manga, /* SY --> */ syncingFlag: Long /* SY <-- */): Manga {
        database.mangaQueries.updateFromBackup(
            userFavoriteAt = manga.favoriteAt,
            // SY -->
            remoteArtist = manga.ogArtist,
            remoteAuthor = manga.ogAuthor,
            remoteDescription = manga.ogDescription,
            remoteGenre = manga.ogGenre,
            remoteTitle = manga.ogTitle,
            remoteStatus = manga.ogStatus,
            remoteCover = manga.ogThumbnailUrl,
            // SY <--
            stateChapterLastUpdate = manga.lastUpdate,
            stateInitialized = manga.initialized,
            userReaderFlags = manga.viewerFlags,
            userChapterFlags = manga.chapterFlags,
            stateCoverLastModified = manga.coverLastModified,
            id = manga.id,
            remoteUpdateStrategy = manga.updateStrategy,
            userNotes = manga.notes,
            remoteMemo = manga.memo,
            // SY -->
            stateVersion = manga.version,
            stateLastModifiedAt = manga.lastModifiedAt,
            stateIsSyncing = syncingFlag,
            // SY <--
        )
        return manga
    }

    private suspend fun insertManga(manga: Manga, /* SY --> */ syncingFlag: Long /* SY <-- */): Long {
        return database.mangaQueries.insertReturningId(
            userFavoriteAt = manga.favoriteAt,
            sourceId = manga.source,
            remoteUrl = manga.url,
            // SY -->
            remoteArtist = manga.ogArtist,
            remoteAuthor = manga.ogAuthor,
            remoteDescription = manga.ogDescription,
            remoteGenre = manga.ogGenre,
            remoteTitle = manga.ogTitle,
            remoteStatus = manga.ogStatus,
            remoteCover = manga.ogThumbnailUrl,
            // SY <--
            stateChapterLastUpdate = manga.lastUpdate,
            stateChapterNextUpdate = null,
            stateChapterFetchInterval = 0L,
            stateInitialized = manga.initialized,
            userReaderFlags = manga.viewerFlags,
            userChapterFlags = manga.chapterFlags,
            stateCoverLastModified = manga.coverLastModified,
            remoteUpdateStrategy = manga.updateStrategy,
            userNotes = manga.notes,
            remoteMemo = manga.memo,
            // SY -->
            stateVersion = manga.version,
            stateLastModifiedAt = manga.lastModifiedAt,
            stateIsSyncing = syncingFlag,
            // SY <--
        )
            .awaitAsOne()
    }

    private suspend fun restoreChapters(manga: Manga, restoredChapters: List<Chapter>, /* SY --> */ syncingFlag: Long /* SY <-- */) {
        val dbChaptersByUrl = chapterRepository.getChapterByMangaId(manga.id)
            .associateBy { it.url }

        val (existingChapters, newChapters) = restoredChapters
            .groupBy { it.url }
            .map { (_, copies) ->
                copies.reduce { kept, other ->
                    kept.copy(
                        read = kept.read || other.read,
                        bookmark = kept.bookmark || other.bookmark,
                        lastPageRead = max(kept.lastPageRead, other.lastPageRead),
                    )
                }
            }
            .mapNotNull {
                val chapter = it.copy(mangaId = manga.id)

                val dbChapter = dbChaptersByUrl[chapter.url]
                    ?: // New chapter
                    return@mapNotNull chapter

                if (chapter.forComparison() == dbChapter.forComparison()) {
                    // Same state; skip
                    return@mapNotNull null
                }

                chapter
                    .copyFrom(dbChapter)
                    .copy(
                        id = dbChapter.id,
                        read = chapter.read || dbChapter.read,
                        bookmark = chapter.bookmark || dbChapter.bookmark,
                        lastPageRead = max(chapter.lastPageRead, dbChapter.lastPageRead),
                        dateFetch = dbChapter.dateFetch,
                        sourceOrder = dbChapter.sourceOrder,
                        // Merge both backup and local data with local winning
                        memo = JsonObject(chapter.memo + dbChapter.memo),
                    )
            }
            .partition { it.id > 0 }

        newChapters.forEach { chapter ->
            database.chapterQueries.insertReturningId(
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
                .awaitAsOneOrNull()
        }
        existingChapters.forEach { chapter ->
            database.chapterQueries.updateFromBackup(
                userRead = chapter.read,
                userBookmark = chapter.bookmark,
                userLastPageRead = chapter.lastPageRead,
                id = chapter.id,
                remoteMemo = chapter.memo,
                // SY -->
                stateLastModifiedAt = chapter.lastModifiedAt,
                stateVersion = chapter.version,
                stateIsSyncing = syncingFlag,
                // SY <--
            )
        }
    }

    private fun Chapter.forComparison() =
        this.copy(id = 0L, mangaId = 0L, dateFetch = Instant.DISTANT_PAST, dateUpload = null)

    private suspend fun restoreHistory(manga: Manga, restoredHistory: List<RestoredHistory>) {
        if (restoredHistory.isEmpty()) return
        val chapterIdsByUrl = chapterRepository.getChapterByMangaId(manga.id).associate { it.url to it.id }
        val dbHistoryByChapterId = database.historyQueries
            .getHistoryByMangaId(manga.id)
            .awaitAsList()
            .associateBy { it.chapter_id }

        val toUpdate = restoredHistory
            // Chapter doesn't exist; skip
            .filter { it.chapterUrl in chapterIdsByUrl }
            // A backup of a library that still had duplicate chapters carries a history entry for
            // each copy; they are one chapter now, read as late as any copy and for as long as all
            .groupBy { chapterIdsByUrl.getValue(it.chapterUrl) }
            .map { (chapterId, copies) ->
                val readAt = copies.mapNotNull { it.readAt }.maxOrNull()
                val readDuration = copies.sumOf { it.readDuration }
                val dbHistory = dbHistoryByChapterId[chapterId]
                    // New history entry
                    ?: return@map Triple(chapterId, readAt, readDuration)

                // Update history entry
                Triple(
                    chapterId,
                    listOfNotNull(readAt, dbHistory.read_at).maxOrNull(),
                    max(readDuration, dbHistory.read_duration) - dbHistory.read_duration,
                )
            }

        toUpdate.forEach { (chapterId, readAt, readDuration) ->
            database.historyQueries.upsert(chapterId = chapterId, readAt = readAt, readDuration = readDuration)
        }
    }

    private suspend fun restoreTracking(manga: Manga, restoredTracks: List<Track>) {
        val dbTrackByTrackerId = trackRepository.getTracksByMangaId(manga.id).associateBy { it.trackerId }

        val (existingTracks, newTracks) = restoredTracks
            .mapNotNull { track ->
                val dbTrack = dbTrackByTrackerId[track.trackerId]
                    ?: // New track
                    return@mapNotNull track.copy(
                        id = 0, // Let DB assign new ID
                        mangaId = manga.id,
                    )

                if (track.lastChapterRead <= dbTrack.lastChapterRead) return@mapNotNull null

                // Update to an existing track
                dbTrack.copy(lastChapterRead = track.lastChapterRead)
            }
            .partition { it.id > 0 }

        if (newTracks.isNotEmpty()) {
            trackRepository.upsertAll(newTracks)
        }

        existingTracks.forEach { track ->
            database.manga_trackQueries.update(
                mangaId = track.mangaId,
                trackerId = track.trackerId,
                remoteId = track.remoteId,
                libraryId = track.libraryId,
                title = track.title,
                lastChapterRead = track.lastChapterRead,
                totalChapters = track.totalChapters,
                status = track.status,
                score = track.score,
                remoteUrl = track.remoteUrl,
                startDate = track.startDate,
                finishDate = track.finishDate,
                `private` = track.private,
                id = track.id,
            )
        }
    }

    private suspend fun restoreExcludedScanlators(manga: Manga, excludedScanlators: List<String>) {
        excludedScanlators.forEach { database.excluded_scanlatorQueries.insert(manga.id, it) }
    }

    // SY -->
    private suspend fun restoreMergedMangaReferences(
        mergeMangaId: Long,
        backupMergedMangaReferences: List<tachiyomi.domain.manga.model.MergedMangaReference>,
    ) {
        if (backupMergedMangaReferences.isEmpty()) return
        val dbMergedMangaReferences = mangaMergeRepository.getAllReferences()
        backupMergedMangaReferences.forEach { reference ->
            if (dbMergedMangaReferences.none {
                    reference.mergeUrl == it.mergeUrl &&
                        reference.mangaUrl == it.mangaUrl
                }
            ) {
                val mergedManga = mangaRepository.getMangaByUrlAndSourceId(
                    reference.mangaUrl,
                    reference.mangaSourceId,
                ) ?: return@forEach
                mangaMergeRepository.insert(
                    reference.copy(
                        mergeId = mergeMangaId,
                        mangaId = mergedManga.id,
                    ),
                )
            }
        }
    }

    private suspend fun restoreFlatMetadata(mangaId: Long, flatMetadata: exh.metadata.metadata.base.FlatMetadata) {
        if (mangaMetadataRepository.getMetadataById(mangaId) == null) {
            mangaMetadataRepository.insertFlatMetadata(
                flatMetadata.copy(
                    metadata = flatMetadata.metadata.copy(mangaId = mangaId),
                    tags = flatMetadata.tags.map { it.copy(mangaId = mangaId) },
                    titles = flatMetadata.titles.map { it.copy(mangaId = mangaId) },
                ),
            )
        }
    }

    private fun restoreCustomMangaInfo(mangaId: Long, customMangaInfo: tachiyomi.domain.manga.model.CustomMangaInfo) {
        customMangaRepository.set(customMangaInfo.copy(id = mangaId))
    }
    // SY <--
}
