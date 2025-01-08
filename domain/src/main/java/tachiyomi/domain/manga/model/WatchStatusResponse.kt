package tachiyomi.domain.manga.model

import kotlinx.serialization.Serializable

@Serializable
data class WatchStatusResponse(
    val id: Int,
    val mangaTitle: String,
    val mangaId: Long,
    val mangaHid: String,
    val chapterCount: Int,
    val interval: Int,
    val deviceToken: String,
)
