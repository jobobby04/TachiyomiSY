package eu.kanade.domain.sync

import eu.kanade.domain.sync.models.SyncSettings
import eu.kanade.tachiyomi.data.sync.models.SyncTriggerOptions
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.util.UUID

class SyncPreferences(
    private val preferenceStore: PreferenceStore,
) {
    val clientHost: Preference<String> = preferenceStore.getString("sync_client_host", "https://sync.tachiyomi.org")
    val clientAPIKey: Preference<String> = preferenceStore.getString("sync_client_api_key", "")
    val lastSyncTimestamp: Preference<Long> = preferenceStore.getLong(Preference.appStateKey("last_sync_timestamp"), 0L)

    val lastSyncEtag: Preference<String> = preferenceStore.getString("sync_etag", "")

    // SyncYomi protocol v2 state
    val syncCursor: Preference<Long> = preferenceStore.getLong(Preference.appStateKey("sync_cursor"), 0L)

    /** Epoch seconds of the start of the last successful sync; the next delta starts here. */
    val lastPushedAt: Preference<Long> = preferenceStore.getLong(Preference.appStateKey("sync_last_pushed_at"), 0L)

    /** Epoch millis of the last sync that carried the complete library. */
    val lastFullSync: Preference<Long> = preferenceStore.getLong(Preference.appStateKey("sync_last_full_sync"), 0L)
    val fullSyncRequested: Preference<Boolean> = preferenceStore.getBoolean(
        Preference.appStateKey("sync_full_requested"),
        false,
    )
    val serverSupportsV2: Preference<Boolean> = preferenceStore.getBoolean(
        Preference.appStateKey("sync_server_supports_v2"),
        false,
    )
    val v2ProbedHost: Preference<String> = preferenceStore.getString(Preference.appStateKey("sync_v2_probed_host"), "")
    val pendingDeletedCategoryUids: Preference<Set<String>> = preferenceStore.getStringSet(
        Preference.appStateKey("sync_pending_deleted_category_uids"),
        emptySet(),
    )

    val syncInterval: Preference<Int> = preferenceStore.getInt("sync_interval", 0)
    val syncService: Preference<Int> = preferenceStore.getInt("sync_service", 0)

    val googleDriveAccessToken: Preference<String> = preferenceStore.getString(
        Preference.appStateKey("google_drive_access_token"),
        "",
    )

    val googleDriveRefreshToken: Preference<String> = preferenceStore.getString(
        Preference.appStateKey("google_drive_refresh_token"),
        "",
    )

    fun uniqueDeviceID(): String {
        val uniqueIDPreference = preferenceStore.getString(Preference.appStateKey("unique_device_id"), "")

        // Retrieve the current value of the preference
        var uniqueID = uniqueIDPreference.get()
        if (uniqueID.isBlank()) {
            uniqueID = UUID.randomUUID().toString()
            uniqueIDPreference.set(uniqueID)
        }

        return uniqueID
    }

    fun isSyncEnabled(): Boolean {
        return syncService.get() != 0
    }

    /**
     * Remembers a deleted category so the next SyncYomi v2 request can send it as a tombstone.
     */
    fun rememberDeletedCategory(uid: Long) {
        if (uid == 0L || !isSyncEnabled()) return
        pendingDeletedCategoryUids.set(pendingDeletedCategoryUids.get() + uid.toString())
    }

    fun getSyncSettings(): SyncSettings {
        return SyncSettings(
            libraryEntries = preferenceStore.getBoolean("library_entries", true).get(),
            categories = preferenceStore.getBoolean("categories", true).get(),
            chapters = preferenceStore.getBoolean("chapters", true).get(),
            tracking = preferenceStore.getBoolean("tracking", true).get(),
            history = preferenceStore.getBoolean("history", true).get(),
            appSettings = preferenceStore.getBoolean("appSettings", true).get(),
            extensionStores = preferenceStore.getBoolean("extensionRepoSettings", true).get(),
            sourceSettings = preferenceStore.getBoolean("sourceSettings", true).get(),
            privateSettings = preferenceStore.getBoolean("privateSettings", true).get(),

            // SY -->
            customInfo = preferenceStore.getBoolean("customInfo", true).get(),
            readEntries = preferenceStore.getBoolean("readEntries", true).get(),
            savedSearches = preferenceStore.getBoolean("savedSearches", true).get(),
            // SY <--
        )
    }

    fun setSyncSettings(syncSettings: SyncSettings) {
        preferenceStore.getBoolean("library_entries", true).set(syncSettings.libraryEntries)
        preferenceStore.getBoolean("categories", true).set(syncSettings.categories)
        preferenceStore.getBoolean("chapters", true).set(syncSettings.chapters)
        preferenceStore.getBoolean("tracking", true).set(syncSettings.tracking)
        preferenceStore.getBoolean("history", true).set(syncSettings.history)
        preferenceStore.getBoolean("appSettings", true).set(syncSettings.appSettings)
        preferenceStore.getBoolean("extensionRepoSettings", true).set(syncSettings.extensionStores)
        preferenceStore.getBoolean("sourceSettings", true).set(syncSettings.sourceSettings)
        preferenceStore.getBoolean("privateSettings", true).set(syncSettings.privateSettings)

        // SY -->
        preferenceStore.getBoolean("customInfo", true).set(syncSettings.customInfo)
        preferenceStore.getBoolean("readEntries", true).set(syncSettings.readEntries)
        preferenceStore.getBoolean("savedSearches", true).set(syncSettings.savedSearches)
        // SY <--
    }

    fun getSyncTriggerOptions(): SyncTriggerOptions {
        return SyncTriggerOptions(
            syncOnChapterRead = preferenceStore.getBoolean("sync_on_chapter_read", false).get(),
            syncOnChapterOpen = preferenceStore.getBoolean("sync_on_chapter_open", false).get(),
            syncOnAppStart = preferenceStore.getBoolean("sync_on_app_start", false).get(),
            syncOnAppResume = preferenceStore.getBoolean("sync_on_app_resume", false).get(),
        )
    }

    fun setSyncTriggerOptions(syncTriggerOptions: SyncTriggerOptions) {
        preferenceStore.getBoolean("sync_on_chapter_read", false)
            .set(syncTriggerOptions.syncOnChapterRead)
        preferenceStore.getBoolean("sync_on_chapter_open", false)
            .set(syncTriggerOptions.syncOnChapterOpen)
        preferenceStore.getBoolean("sync_on_app_start", false)
            .set(syncTriggerOptions.syncOnAppStart)
        preferenceStore.getBoolean("sync_on_app_resume", false)
            .set(syncTriggerOptions.syncOnAppResume)
    }
}
