package eu.kanade.tachiyomi.data.storage.gdrive

import tachiyomi.core.common.preference.PreferenceStore

class GoogleDrivePreferences(
    private val preferenceStore: PreferenceStore,
) {
    /** Serialized [GoogleDriveOAuth]; empty when signed out. */
    val token = preferenceStore.getString(TOKEN_KEY, "")

    /** Shown in settings so the user can tell which account is linked. */
    val accountEmail = preferenceStore.getString("gdrive_account_email", "")

    /** OAuth credentials from the user's own Google Cloud project. */
    val clientId = preferenceStore.getString("gdrive_client_id", "")
    val clientSecret = preferenceStore.getString("gdrive_client_secret", "")

    /** Drive file id of the folder Mihon stores everything under. */
    val rootFolderId = preferenceStore.getString("gdrive_root_folder_id", "")

    companion object {
        const val TOKEN_KEY = "gdrive_oauth_token"
    }
}
