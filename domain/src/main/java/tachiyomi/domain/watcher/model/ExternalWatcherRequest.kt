package tachiyomi.domain.watcher.model

import kotlinx.serialization.Serializable

@Serializable
data class ExternalWatcherRequest(
    val mangaTitle: String,
    val mangaId: Int,
    val mangaHid: String,
    val interval: Long,
    val deviceToken: String,
)
