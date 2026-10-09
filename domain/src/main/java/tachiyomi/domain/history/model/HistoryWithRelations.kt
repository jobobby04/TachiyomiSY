package tachiyomi.domain.history.model

import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.MangaCover
import kotlin.time.Instant

data class HistoryWithRelations(
    val id: Long,
    val chapterId: Long,
    val mangaId: Long,
    // SY -->
    val ogTitle: String,
    val customMangaInfo: CustomMangaInfo? = null,
    // SY <--
    val chapterNumber: Double,
    val readAt: Instant?,
    val readDuration: Long,
    val coverData: MangaCover,
) {
    // SY -->
    val title: String = customMangaInfo?.title ?: ogTitle
    // SY <--
}
