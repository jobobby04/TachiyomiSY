package tachiyomi.domain.manga.model

import exh.metadata.metadata.EHentaiSearchMetadata

/**
 * SY-specific file
 */
data class FavoriteEntry(

    val title: String,

    val gid: String,

    val token: String,

    val otherGid: String? = null,

    val otherToken: String? = null,

    val category: Int = -1,
) {
    fun getUrl() = EHentaiSearchMetadata.idAndTokenToUrl(gid, token)
}
