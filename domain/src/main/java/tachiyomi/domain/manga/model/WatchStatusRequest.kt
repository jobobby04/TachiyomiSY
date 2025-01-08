package tachiyomi.domain.manga.model

import kotlinx.serialization.Serializable

@Serializable
data class WatchStatusRequest(
    val mangaTitle: String,
    val mangaId: Int,
    val mangaHid: String,
    val interval: Long,
    val deviceToken: String,
)
