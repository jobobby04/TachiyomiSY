package tachiyomi.domain.manga.model

import kotlin.time.Instant

/**
 * Contains the required data for MangaCoverFetcher
 */
data class MangaCover(
    val mangaId: Long,
    val sourceId: Long,
    val isMangaFavorite: Boolean,
    // SY -->
    val ogUrl: String?,
    val userThumbnailUrl: String? = null,
    // SY <--
    val lastModified: Instant?,
) {
    // SY -->
    val url: String? = userThumbnailUrl ?: ogUrl
    // SY <--
}

fun Manga.asMangaCover(): MangaCover {
    return MangaCover(
        mangaId = id,
        sourceId = source,
        isMangaFavorite = favorite,
        // SY -->
        ogUrl = thumbnailUrl,
        // SY <--
        lastModified = coverLastModified,
    )
}
