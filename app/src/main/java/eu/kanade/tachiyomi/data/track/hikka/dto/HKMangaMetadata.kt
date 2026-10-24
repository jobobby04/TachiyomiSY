package eu.kanade.tachiyomi.data.track.hikka.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * SY-specific file
 */
@Serializable
data class HKMangaMetadata(
    @SerialName("title_original")
    val titleOriginal: String,
    @SerialName("title_ua")
    val titleUa: String? = null,
    @SerialName("title_en")
    val titleEn: String? = null,
    val image: String,
    @SerialName("synopsis_ua")
    val synopsisUa: String? = null,
    @SerialName("synopsis_en")
    val synopsisEn: String? = null,
    val authors: List<HKAuthor>,
    val slug: String,
)

@Serializable
data class HKAuthor(
    val roles: List<HKAuthorRole>,
    val person: HKAuthorPerson,
)

@Serializable
data class HKAuthorRole(
    val slug: String,
)

@Serializable
data class HKAuthorPerson(
    @SerialName("name_native")
    val nameNative: String?,
    @SerialName("name_ua")
    val nameUa: String?,
    @SerialName("name_en")
    val nameEn: String?,
)
