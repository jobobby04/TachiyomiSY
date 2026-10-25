package tachiyomi.domain.manga.model

/**
 * SY-specific file
 */
data class MergeMangaSettingsUpdate(
    val id: Long,
    var isInfoManga: Boolean?,
    var getChapterUpdates: Boolean?,
    var chapterPriority: Int?,
    var downloadChapters: Boolean?,
    var chapterSortMode: Int?,
)
