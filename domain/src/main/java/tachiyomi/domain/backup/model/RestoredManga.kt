package tachiyomi.domain.backup.model

import exh.metadata.metadata.base.FlatMetadata
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.model.Track
import kotlin.time.Instant

data class RestoredManga(
    val manga: Manga,
    val chapters: List<Chapter>,
    val categoryIds: List<Long>,
    val history: List<RestoredHistory>,
    val tracks: List<Track>,
    val excludedScanlators: List<String>,
    // SY -->
    val mergedEntries: List<RestoredMergedEntry> = emptyList(),
    val mergeSettings: RestoredMergeSettings? = null,
    val flatMetadata: FlatMetadata? = null,
    // SY <--
)

data class RestoredHistory(
    val chapterUrl: String,
    val readAt: Instant?,
    val readDuration: Long,
)

// SY -->
data class RestoredMergedEntry(
    val mangaUrl: String,
    val mangaSourceId: Long,
    val fetchChapterUpdates: Boolean,
    val downloadChapters: Boolean,
    val chapterPriority: Int,
)

data class RestoredMergeSettings(
    val chapterSortMode: Int,
    val infoMangaUrl: String,
    val infoMangaSourceId: Long,
)
// SY <--
