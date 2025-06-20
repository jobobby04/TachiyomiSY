package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ALUserMangaListQueryResult(
    val data: ALUserMangaListPage,
)

@Serializable
data class ALUserMangaListPage(
    @SerialName("Page")
    val page: ALUserMangaListMediaList,
)

@Serializable
data class ALUserMangaListMediaList(
    val mediaList: List<ALUserMediaListEntry>,
)

@Serializable
data class ALUserMediaListEntry(
    val media: ALUserMediaListEntryMedia,
)

@Serializable
data class ALUserMediaListEntryMedia(
    val id: Long,
    val title: ALItemTitle,
    val coverImage: ItemCover,
)
