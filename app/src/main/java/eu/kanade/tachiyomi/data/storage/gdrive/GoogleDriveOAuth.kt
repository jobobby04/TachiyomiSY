package eu.kanade.tachiyomi.data.storage.gdrive

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OAuth token set for Google Drive, persisted as JSON in the preference store.
 *
 * [obtainedAt] is stamped locally when the token is received, since Google only
 * reports a relative [expiresIn].
 */
@Serializable
data class GoogleDriveOAuth(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("scope") val scope: String? = null,
    @SerialName("obtained_at") val obtainedAt: Long = 0,
) {
    /** Treats the token as expired a minute early to avoid racing the boundary. */
    fun isExpired(): Boolean =
        System.currentTimeMillis() > obtainedAt + (expiresIn - 60).coerceAtLeast(0) * 1000

    fun stamped(refreshTokenFallback: String? = null): GoogleDriveOAuth = copy(
        refreshToken = refreshToken ?: refreshTokenFallback,
        obtainedAt = System.currentTimeMillis(),
    )
}
