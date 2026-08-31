package eu.kanade.tachiyomi.data.storage.gdrive

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** Attaches the bearer token to every Drive call, refreshing it when it has aged out. */
class GoogleDriveInterceptor(
    private val service: GoogleDriveService,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = currentToken()
        return chain.proceed(
            chain.request().newBuilder()
                .header("Authorization", "Bearer ${token.accessToken}")
                .build(),
        )
    }

    private fun currentToken(): GoogleDriveOAuth = synchronized(this) {
        val stored = service.loadToken()
            ?: throw IOException("Google Drive: not signed in")
        if (!stored.isExpired()) return stored

        val refreshed = service.auth.refresh(stored)
        service.saveToken(refreshed)
        return refreshed
    }
}
