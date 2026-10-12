package eu.kanade.tachiyomi.data.backup.create.creators

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupFlatMetadata
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.toBackupChapter
import eu.kanade.tachiyomi.data.backup.models.toBackupMergedMangaReference
import eu.kanade.tachiyomi.data.backup.models.toBackupTracking
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mihon.core.common.extensions.toByteArray
import mihon.core.common.extensions.toEpochMillisOrZero
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaMergeRepository
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.repository.TrackRepository

@Inject
class MangaBackupCreator(
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val categoryRepository: CategoryRepository,
    private val historyRepository: HistoryRepository,
    private val trackRepository: TrackRepository,
    // SY -->
    private val mangaMergeRepository: MangaMergeRepository,
    private val sourceManager: SourceManager,
    private val getCustomMangaInfo: GetCustomMangaInfo,
    private val getFlatMetadataById: GetFlatMetadataById,
    // SY <--
) {

    /**
     * Builds the entries a chunk at a time, so only one chunk's chapters are held at once.
     */
    operator fun invoke(mangas: List<Manga>, options: BackupOptions): Flow<BackupManga> = flow {
        mangas.chunked(CHUNK_SIZE).forEach { chunk ->
            backupMangas(chunk, options).forEach { emit(it) }
        }
    }

    private suspend fun backupMangas(mangas: List<Manga>, options: BackupOptions): List<BackupManga> {
        val mangaIds = mangas.map { it.id }

        val excludedScanlatorsByMangaId = mangaRepository.getExcludedScanlatorsByMangaIds(mangaIds)
        // History is backed up by chapter URL, so it needs the chapters too
        val chaptersByMangaId = if (options.chapters || options.history) {
            chapterRepository.getChaptersByMangaIds(mangaIds).groupBy { it.mangaId }
        } else {
            emptyMap()
        }
        val categoriesByMangaId = if (options.categories) {
            categoryRepository.getCategoriesByMangaIds(mangaIds)
        } else {
            emptyMap()
        }
        val tracksByMangaId = if (options.tracking) {
            trackRepository.getTracksByMangaIds(mangaIds).groupBy { it.mangaId }
        } else {
            emptyMap()
        }
        val historyByMangaId = if (options.history) historyRepository.getHistoryByMangaIds(mangaIds) else emptyMap()

        return mangas.map { manga ->
            val mangaObject = manga.toBackupManga(
                // SY -->
                if (options.customInfo) {
                    getCustomMangaInfo.get(manga.id)
                } else {
                    null
                }, /* SY <-- */
            )
            val chapters = chaptersByMangaId[manga.id].orEmpty()

            // SY -->
            if (manga.source == MERGED_SOURCE_ID) {
                mangaObject.mergedMangaReferences = mangaMergeRepository.getReferencesById(manga.id)
                    .map { it.toBackupMergedMangaReference() }
            }

            val source = sourceManager.get(manga.source)?.getMainSource<MetadataSource<*, *>>()
            if (source != null) {
                getFlatMetadataById.await(manga.id)?.let { flatMetadata ->
                    mangaObject.flatMetadata = BackupFlatMetadata.copyFrom(flatMetadata)
                }
            }
            // SY <--

            mangaObject.excludedScanlators = excludedScanlatorsByMangaId[manga.id].orEmpty()

            if (options.chapters) {
                mangaObject.chapters = chapters.map { it.toBackupChapter() }
            }

            if (options.categories) {
                mangaObject.categories = categoriesByMangaId[manga.id].orEmpty().map { it.order }
            }

            if (options.tracking) {
                mangaObject.tracking = tracksByMangaId[manga.id].orEmpty().map { it.toBackupTracking() }
            }

            if (options.history) {
                val chapterUrlsById = chapters.associate { it.id to it.url }
                mangaObject.history = historyByMangaId[manga.id].orEmpty().mapNotNull { history ->
                    // A chapter removed since its history was read takes that history with it
                    val url = chapterUrlsById[history.chapterId] ?: return@mapNotNull null
                    BackupHistory(url, history.readAt.toEpochMillisOrZero(), history.readDuration)
                }
            }

            mangaObject
        }
    }

    private companion object {
        const val CHUNK_SIZE = 100
    }
}

private fun Manga.toBackupManga(/* SY --> */customMangaInfo: CustomMangaInfo?/* SY <-- */) =
    BackupManga(
        url = this.url,
        // SY -->
        title = this.ogTitle,
        artist = this.ogArtist,
        author = this.ogAuthor,
        description = this.ogDescription,
        genre = this.ogGenre.orEmpty(),
        status = this.ogStatus.toInt(),
        thumbnailUrl = this.ogThumbnailUrl,
        // SY <--
        favorite = this.favorite,
        source = this.source,
        dateAdded = this.favoriteAt.toEpochMillisOrZero(),
        viewer = (this.viewerFlags.toInt() and ReadingMode.MASK),
        viewer_flags = this.viewerFlags.toInt(),
        chapterFlags = this.chapterFlags.toInt(),
        updateStrategy = this.updateStrategy,
        // SY -->
        lastModifiedAt = this.lastModifiedAt,
        version = this.version,
        // SY <--
        notes = this.notes,
        initialized = this.initialized,
        memo = this.memo.toByteArray(),
        nextUpdate = this.nextUpdate.toEpochMillisOrZero(),
        // SY -->
    ).also { backupManga ->
        customMangaInfo?.let {
            backupManga.customTitle = it.title
            backupManga.customArtist = it.artist
            backupManga.customAuthor = it.author
            backupManga.customThumbnailUrl = it.thumbnailUrl
            backupManga.customDescription = it.description
            backupManga.customGenre = it.genre
            backupManga.customStatus = it.status?.toInt() ?: 0
        }
    }
// SY <--
