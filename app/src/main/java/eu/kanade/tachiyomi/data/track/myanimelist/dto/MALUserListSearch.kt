package eu.kanade.tachiyomi.data.track.myanimelist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MALUserSearchResult(
    val data: List<MALUserSearchItem>,
    val paging: MALUserSearchPaging,
)

@Serializable
data class MALUserSearchItem(
    val node: MALUserSearchItemNode,
    @SerialName("list_status")
    val listStatus: MALListItemStatus?,
)

@Serializable
data class MALUserSearchPaging(
    val next: String?,
)

@Serializable
data class MALUserSearchItemNode(
    val id: Int,
    val title: String,
    @SerialName("main_picture")
    val covers: MALMangaCovers?,
)
