package exh.eh

import kotlinx.serialization.Serializable

/**
 * SY-specific file
 */
@Serializable
data class EHentaiUpdaterStats(
    val startTime: Long,
    val possibleUpdates: Int,
    val updateCount: Int,
)
