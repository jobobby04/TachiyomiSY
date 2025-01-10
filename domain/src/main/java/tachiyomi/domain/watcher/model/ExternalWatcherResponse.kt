package tachiyomi.domain.watcher.model

import kotlinx.serialization.Serializable

@Serializable
data class ExternalWatcherResponse(
    val id: Int,
    val mangaTitle: String,
    val mangaId: Long,
    val mangaHid: String,
    val deviceToken: String,
)
