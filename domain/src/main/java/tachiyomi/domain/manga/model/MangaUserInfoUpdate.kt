package tachiyomi.domain.manga.model

/**
 * SY-specific file
 *
 * The user's edited details for a single manga, used to update the manga row in one batch.
 */
data class MangaUserInfoUpdate(
    val id: Long,
    val title: String?,
    val author: String?,
    val artist: String?,
    val thumbnailUrl: String?,
    val description: String?,
    val genre: List<String>?,
    val status: Long?,
)
