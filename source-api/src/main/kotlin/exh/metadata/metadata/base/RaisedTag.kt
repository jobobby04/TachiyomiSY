package exh.metadata.metadata.base

import kotlinx.serialization.Serializable

/**
 * SY-specific file
 */
@Serializable
data class RaisedTag(
    val namespace: String?,
    val name: String,
    val type: Int,
)
