package tachiyomi.domain.manga.model

/**
 * SY-specific file
 */
data class MergedEntry(
    val id: Long,
    val mergeId: Long,
    val mangaId: Long,
    val fetchChapterUpdates: Boolean,
    val downloadChapters: Boolean,
    val chapterPriority: Int,
)
