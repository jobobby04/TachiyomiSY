package tachiyomi.domain.history.model

/**
 * SY-specific file
 *
 * The identity of a history row as the sync server keys it, so clearing it locally can be tombstoned.
 */
data class HistoryTombstoneKey(
    val chapterId: Long,
    val mangaSourceId: Long,
    val mangaUrl: String,
    val chapterUrl: String,
)
