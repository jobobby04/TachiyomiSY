package eu.kanade.tachiyomi.data.storage.gdrive

import android.app.Application
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.OkHttpClient
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Entry point for everything Google Drive: credentials, the authenticated HTTP
 * client, and the folder the app keeps its library in.
 */
class GoogleDriveService(
    private val context: Application,
    networkHelper: NetworkHelper,
    val preferences: GoogleDrivePreferences,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Outlives any screen. The redirect only arrives after the user has left for
     * the browser, so a composition-scoped job would be cancelled — closing the
     * loopback socket — before Google ever calls back.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Deliberately unauthenticated: token calls must not recurse through the interceptor. */
    val auth = GoogleDriveAuth(networkHelper.client, preferences)

    private val authedClient: OkHttpClient by lazy {
        networkHelper.client.newBuilder()
            .addInterceptor(GoogleDriveInterceptor(this))
            .build()
    }

    val api: GoogleDriveApi by lazy { GoogleDriveApi(authedClient) }

    val isConfigured: Boolean
        get() = auth.getEffectiveClientId().isNotBlank() &&
            auth.getEffectiveClientSecret().isNotBlank()

    val isSignedIn: Boolean
        get() = loadToken() != null

    fun loadToken(): GoogleDriveOAuth? = try {
        preferences.token.get()
            .takeIf { it.isNotBlank() }
            ?.let { json.decodeFromString<GoogleDriveOAuth>(it) }
    } catch (_: Exception) {
        null
    }

    fun saveToken(token: GoogleDriveOAuth?) {
        preferences.token.set(token?.let { json.encodeToString(it) }.orEmpty())
    }

    fun signOut() {
        saveToken(null)
        preferences.accountEmail.set("")
        preferences.rootFolderId.set("")
        try {
            val storagePreferences = Injekt.get<StoragePreferences>()
            if (storagePreferences.baseStorageDirectory.get().startsWith(URI_SCHEME)) {
                storagePreferences.baseStorageDirectory.delete()
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to reset storage directory on sign out" }
        }
    }

    /**
     * Waits for Google to hit the loopback socket opened by [session], then stores
     * the token and automatically configures Google Drive as the storage location.
     */
    fun completeSignIn(session: GoogleDriveAuth.Session) {
        GoogleDriveAuthService.start(context)
        scope.launch {
            logcat(LogPriority.INFO) { "gdrive: completeSignIn started" }
            val message = try {
                saveToken(auth.awaitToken(session))
                try {
                    val about = api.getAbout()
                    val email = about?.user?.emailAddress ?: about?.user?.displayName
                    if (!email.isNullOrBlank()) {
                        preferences.accountEmail.set(email)
                    }
                } catch (_: Exception) {
                }
                try {
                    val folderId = ensureRootFolder()
                    val storagePreferences = Injekt.get<StoragePreferences>()
                    storagePreferences.baseStorageDirectory.set("${GoogleDriveService.URI_SCHEME}://$folderId")
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to auto-configure Google Drive storage directory" }
                }
                context.stringResource(SYMR.strings.gdrive_sign_in_success)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Google Drive sign-in failed" }
                e.message ?: "Google Drive sign-in failed"
            } finally {
                GoogleDriveAuthService.stop(context)
            }
            withContext(Dispatchers.Main) { context.toast(message) }
        }
    }

    /**
     * Finds — or creates — the app's library folder and caches its id.
     *
     * With the `drive.file` scope, listing `root` only ever surfaces entries this
     * app made, so a name match here cannot collide with the user's own files.
     */
    fun ensureRootFolder(): String {
        preferences.rootFolderId.get()
            .takeIf { it.isNotBlank() }
            ?.let { cached -> if (api.getEntry(cached)?.trashed == false) return cached }

        val existing = api.listChildren("root")
            .firstOrNull { it.isFolder && it.name == LIBRARY_FOLDER_NAME }
        val folder = existing ?: api.createFolder("root", LIBRARY_FOLDER_NAME)
        preferences.rootFolderId.set(folder.id)
        return folder.id
    }

    companion object {
        const val LIBRARY_FOLDER_NAME = "TachiyomiSY"

        /** Scheme used by the storage-location preference, e.g. `gdrive://root`. */
        const val URI_SCHEME = "gdrive"
    }
}
