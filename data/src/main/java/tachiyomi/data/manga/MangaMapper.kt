package tachiyomi.data.manga

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.json.JsonObject
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.view.LibraryView
import kotlin.time.Instant

@Inject
class MangaMapper(
    private val getCustomMangaInfo: GetCustomMangaInfo,
) {
    fun mapManga(
        id: Long,
        sourceId: Long,
        remoteUrl: String,
        remoteTitle: String,
        remoteAuthor: String?,
        remoteArtist: String?,
        remoteDescription: String?,
        remoteGenre: List<String>?,
        remoteStatus: Long,
        remoteCover: String?,
        remoteUpdateStrategy: UpdateStrategy,
        remoteMemo: JsonObject,
        userFavoriteAt: Instant?,
        userNotes: String,
        userReaderFlags: Long,
        userChapterFlags: Long,
        stateChapterLastUpdate: Instant?,
        stateChapterNextUpdate: Instant?,
        stateChapterFetchInterval: Long,
        stateCoverLastModified: Instant?,
        stateInitialized: Boolean,
        // SY -->
        stateLastModifiedAt: Long,
        stateVersion: Long,
        stateIsSyncing: Long,
        // SY <--
    ): Manga = Manga(
        id = id,
        source = sourceId,
        favoriteAt = userFavoriteAt,
        lastUpdate = stateChapterLastUpdate,
        nextUpdate = stateChapterNextUpdate,
        fetchInterval = stateChapterFetchInterval.toInt(),
        viewerFlags = userReaderFlags,
        chapterFlags = userChapterFlags,
        coverLastModified = stateCoverLastModified,
        url = remoteUrl,
        // SY -->
        ogTitle = remoteTitle,
        ogArtist = remoteArtist,
        ogAuthor = remoteAuthor,
        ogDescription = remoteDescription,
        ogGenre = remoteGenre,
        ogStatus = remoteStatus,
        ogThumbnailUrl = remoteCover,
        // SY <--
        updateStrategy = remoteUpdateStrategy,
        initialized = stateInitialized,
        // SY -->
        lastModifiedAt = stateLastModifiedAt,
        version = stateVersion,
        // SY <--
        notes = userNotes,
        memo = remoteMemo,
        // SY -->
        customMangaInfo = if (userFavoriteAt != null) getCustomMangaInfo.get(id) else null,
        // SY <--
    )

    fun mapLibraryManga(
        id: Long,
        sourceId: Long,
        remoteUrl: String,
        remoteTitle: String,
        remoteAuthor: String?,
        remoteArtist: String?,
        remoteDescription: String?,
        remoteGenre: List<String>?,
        remoteStatus: Long,
        remoteCover: String?,
        remoteUpdateStrategy: UpdateStrategy,
        remoteMemo: JsonObject,
        userFavoriteAt: Instant?,
        userNotes: String,
        userReaderFlags: Long,
        userChapterFlags: Long,
        stateChapterLastUpdate: Instant?,
        stateChapterNextUpdate: Instant?,
        stateChapterFetchInterval: Long,
        stateCoverLastModified: Instant?,
        stateInitialized: Boolean,
        // SY -->
        stateLastModifiedAt: Long,
        stateVersion: Long,
        stateIsSyncing: Long,
        // SY <--
        totalCount: Long,
        readCount: Double,
        latestUpload: Instant?,
        chapterFetchedAt: Instant?,
        lastRead: Instant?,
        bookmarkCount: Double,
        categories: String,
    ): LibraryManga = LibraryManga(
        manga = mapManga(
            id = id,
            sourceId = sourceId,
            remoteUrl = remoteUrl,
            remoteTitle = remoteTitle,
            remoteAuthor = remoteAuthor,
            remoteArtist = remoteArtist,
            remoteDescription = remoteDescription,
            remoteGenre = remoteGenre,
            remoteStatus = remoteStatus,
            remoteCover = remoteCover,
            remoteUpdateStrategy = remoteUpdateStrategy,
            remoteMemo = remoteMemo,
            userFavoriteAt = userFavoriteAt,
            userNotes = userNotes,
            userReaderFlags = userReaderFlags,
            userChapterFlags = userChapterFlags,
            stateChapterLastUpdate = stateChapterLastUpdate,
            stateChapterNextUpdate = stateChapterNextUpdate,
            stateChapterFetchInterval = stateChapterFetchInterval,
            stateCoverLastModified = stateCoverLastModified,
            stateInitialized = stateInitialized,
            // SY -->
            stateLastModifiedAt = stateLastModifiedAt,
            stateVersion = stateVersion,
            stateIsSyncing = stateIsSyncing,
            // SY <--
        ),
        categories = categories.split(",").map { it.toLong() },
        totalChapters = totalCount,
        readCount = readCount.toLong(),
        bookmarkCount = bookmarkCount.toLong(),
        latestUpload = latestUpload,
        chapterFetchedAt = chapterFetchedAt,
        lastRead = lastRead,
    )

    fun mapMangaWithChapterCount(
        id: Long,
        sourceId: Long,
        remoteUrl: String,
        remoteTitle: String,
        remoteAuthor: String?,
        remoteArtist: String?,
        remoteDescription: String?,
        remoteGenre: List<String>?,
        remoteStatus: Long,
        remoteCover: String?,
        remoteUpdateStrategy: UpdateStrategy,
        remoteMemo: JsonObject,
        userFavoriteAt: Instant?,
        userNotes: String,
        userReaderFlags: Long,
        userChapterFlags: Long,
        stateChapterLastUpdate: Instant?,
        stateChapterNextUpdate: Instant?,
        stateChapterFetchInterval: Long,
        stateCoverLastModified: Instant?,
        stateInitialized: Boolean,
        // SY -->
        stateLastModifiedAt: Long,
        stateVersion: Long,
        stateIsSyncing: Long,
        // SY <--
        chapterCount: Long,
    ): MangaWithChapterCount = MangaWithChapterCount(
        manga = mapManga(
            id = id,
            sourceId = sourceId,
            remoteUrl = remoteUrl,
            remoteTitle = remoteTitle,
            remoteAuthor = remoteAuthor,
            remoteArtist = remoteArtist,
            remoteDescription = remoteDescription,
            remoteGenre = remoteGenre,
            remoteStatus = remoteStatus,
            remoteCover = remoteCover,
            remoteUpdateStrategy = remoteUpdateStrategy,
            remoteMemo = remoteMemo,
            userFavoriteAt = userFavoriteAt,
            userNotes = userNotes,
            userReaderFlags = userReaderFlags,
            userChapterFlags = userChapterFlags,
            stateChapterLastUpdate = stateChapterLastUpdate,
            stateChapterNextUpdate = stateChapterNextUpdate,
            stateChapterFetchInterval = stateChapterFetchInterval,
            stateCoverLastModified = stateCoverLastModified,
            stateInitialized = stateInitialized,
            // SY -->
            stateLastModifiedAt = stateLastModifiedAt,
            stateVersion = stateVersion,
            stateIsSyncing = stateIsSyncing,
            // SY <--
        ),
        chapterCount = chapterCount,
    )

    fun mapLibraryView(libraryView: LibraryView): LibraryManga {
        return LibraryManga(
            manga = Manga(
                id = libraryView.id,
                source = libraryView.source_id,
                favoriteAt = libraryView.user_favorite_at,
                lastUpdate = libraryView.state_chapter_last_update,
                nextUpdate = libraryView.state_chapter_next_update,
                viewerFlags = libraryView.user_reader_flags,
                chapterFlags = libraryView.user_chapter_flags,
                coverLastModified = libraryView.state_cover_last_modified,
                url = libraryView.remote_url,
                ogTitle = libraryView.remote_title,
                ogArtist = libraryView.remote_artist,
                ogAuthor = libraryView.remote_author,
                ogDescription = libraryView.remote_description,
                ogGenre = libraryView.remote_genre,
                ogStatus = libraryView.remote_status,
                ogThumbnailUrl = libraryView.remote_cover,
                updateStrategy = libraryView.remote_update_strategy,
                initialized = libraryView.state_initialized,
                fetchInterval = libraryView.state_chapter_fetch_interval.toInt(),
                // SY -->
                lastModifiedAt = libraryView.state_last_modified_at,
                version = libraryView.state_version,
                // SY <--
                notes = libraryView.user_notes,
                memo = libraryView.remote_memo,
                // SY -->
                customMangaInfo = if (libraryView.user_favorite_at != null) getCustomMangaInfo.get(libraryView.id) else null,
                // SY <--
            ),
            categories = libraryView.categories.split(",").map { it.toLong() },
            totalChapters = libraryView.totalCount,
            readCount = libraryView.readCount.toLong(),
            bookmarkCount = libraryView.bookmarkCount.toLong(),
            latestUpload = libraryView.latestUpload,
            chapterFetchedAt = libraryView.chapterFetchedAt,
            lastRead = libraryView.lastRead,
        )
    }
}
