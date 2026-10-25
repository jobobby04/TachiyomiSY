package tachiyomi.domain.updates.model

import tachiyomi.domain.manga.model.MangaCover
import kotlin.time.Instant

data class UpdatesWithRelations(
    val mangaId: Long,
    // SY -->
    val ogMangaTitle: String,
    val userTitle: String? = null,
    // SY <--
    val chapterId: Long,
    val chapterName: String,
    val scanlator: String?,
    val chapterUrl: String,
    val read: Boolean,
    val bookmark: Boolean,
    val lastPageRead: Int,
    val sourceId: Long,
    val dateFetch: Instant,
    val coverData: MangaCover,
) {
    // SY -->
    val mangaTitle: String = userTitle ?: ogMangaTitle
    // SY <--
}
