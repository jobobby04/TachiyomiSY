package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.download.DownloadCache
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.source.local.isLocal

/**
 * Returns a copy of the list with not downloaded chapters removed.
 */
fun List<Chapter>.filterDownloaded(
    manga: Manga,
    downloadCache: DownloadCache,
    // SY -->
    mangaMap: Map<Long, Manga>?,
    // SY <--
): List<Chapter> {
    if (manga.isLocal()) return this

    // SY -->
    return filter {
        val chapterManga = mangaMap?.get(it.mangaId) ?: manga
        downloadCache.isChapterDownloaded(it.name, it.scanlator, it.url, chapterManga.ogTitle, chapterManga.source)
    }
    // SY <--
}
