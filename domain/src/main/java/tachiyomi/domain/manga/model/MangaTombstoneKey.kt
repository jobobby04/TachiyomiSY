package tachiyomi.domain.manga.model

/**
 * SY-specific file
 *
 * The identity of a manga as the sync server keys it, so a local deletion can be tombstoned.
 */
data class MangaTombstoneKey(
    val sourceId: Long,
    val url: String,
)
