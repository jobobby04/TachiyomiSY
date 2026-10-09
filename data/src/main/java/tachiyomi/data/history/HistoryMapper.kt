package tachiyomi.data.history

import dev.zacsweers.metro.Inject
import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.model.MangaCover
import kotlin.time.Instant

@Inject
class HistoryMapper(
    private val getCustomMangaInfo: GetCustomMangaInfo,
) {
    fun mapHistory(
        id: Long,
        chapterId: Long,
        readAt: Instant?,
        readDuration: Long,
    ): History = History(
        id = id,
        chapterId = chapterId,
        readAt = readAt,
        readDuration = readDuration,
    )

    fun mapHistoryWithRelations(
        historyId: Long,
        mangaId: Long,
        chapterId: Long,
        title: String,
        thumbnailUrl: String?,
        sourceId: Long,
        isFavorite: Boolean,
        coverLastModified: Instant?,
        chapterNumber: Double,
        readAt: Instant?,
        readDuration: Long,
    ): HistoryWithRelations {
        val customMangaInfo = getCustomMangaInfo.get(mangaId)
        return HistoryWithRelations(
            id = historyId,
            chapterId = chapterId,
            mangaId = mangaId,
            // SY -->
            ogTitle = title,
            customMangaInfo = customMangaInfo,
            // SY <--
            chapterNumber = chapterNumber,
            readAt = readAt,
            readDuration = readDuration,
            coverData = MangaCover(
                mangaId = mangaId,
                sourceId = sourceId,
                isMangaFavorite = isFavorite,
                ogUrl = thumbnailUrl,
                customThumbnailUrl = if (isFavorite) customMangaInfo?.thumbnailUrl else null,
                lastModified = coverLastModified,
            ),
        )
    }
}
