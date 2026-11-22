package tachiyomi.data.manga

import tachiyomi.domain.manga.model.MergeSettings
import tachiyomi.domain.manga.model.MergedEntry

/**
 * SY-specific file
 */
object MergedMangaMapper {
    fun mapMergeSettings(
        mangaId: Long,
        chapterSortMode: Long,
        infoMangaId: Long?,
    ): MergeSettings {
        return MergeSettings(
            mangaId = mangaId,
            chapterSortMode = chapterSortMode.toInt(),
            infoMangaId = infoMangaId,
        )
    }

    fun mapEntry(
        id: Long,
        mergeId: Long,
        mangaId: Long,
        fetchChapterUpdates: Boolean,
        downloadChapters: Boolean,
        chapterPriority: Long,
    ): MergedEntry {
        return MergedEntry(
            id = id,
            mergeId = mergeId,
            mangaId = mangaId,
            fetchChapterUpdates = fetchChapterUpdates,
            downloadChapters = downloadChapters,
            chapterPriority = chapterPriority.toInt(),
        )
    }
}
