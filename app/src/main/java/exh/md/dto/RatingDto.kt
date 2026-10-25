package exh.md.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * SY-specific file
 */
@Serializable
data class RatingResponseDto(
    val ratings: JsonElement,
)

@Serializable
data class PersonalRatingDto(
    val rating: Int,
    val createdAt: String,
)

@Serializable
data class RatingDto(val rating: Int)
