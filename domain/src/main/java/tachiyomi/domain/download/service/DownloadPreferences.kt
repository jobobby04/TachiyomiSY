package tachiyomi.domain.download.service

import tachiyomi.core.common.preference.PreferenceStore

class DownloadPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun downloadOnlyOverWifi() = preferenceStore.getBoolean(
        "pref_download_only_over_wifi_key",
        true,
    )

    fun saveChaptersAsCBZ() = preferenceStore.getBoolean("save_chapter_as_cbz", true)

    fun splitTallImages() = preferenceStore.getBoolean("split_tall_images", true)

    fun autoDownloadWhileReading() = preferenceStore.getInt("auto_download_while_reading", 0)

    fun removeAfterReadSlots() = preferenceStore.getInt("remove_after_read_slots", -1)

    fun removeAfterMarkedAsRead() = preferenceStore.getBoolean(
        "pref_remove_after_marked_as_read_key",
        false,
    )

    fun removeBookmarkedChapters() = preferenceStore.getBoolean("pref_remove_bookmarked", false)

    fun removeExcludeCategories() = preferenceStore.getStringSet(REMOVE_EXCLUDE_CATEGORIES_PREF_KEY, emptySet())

    fun downloadNewChapters() = preferenceStore.getBoolean("download_new", false)

    fun downloadNewChapterCategories() = preferenceStore.getStringSet(DOWNLOAD_NEW_CATEGORIES_PREF_KEY, emptySet())

    fun downloadNewChapterCategoriesExclude() =
        preferenceStore.getStringSet(DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY, emptySet())

    fun downloadNewUnreadChaptersOnly() = preferenceStore.getBoolean("download_new_unread_chapters_only", false)

    fun parallelSourceLimit() = preferenceStore.getInt("download_parallel_source_limit", 5)

    fun parallelPageLimit() = preferenceStore.getInt("download_parallel_page_limit", 5)

    /**
     * Indicates whether the chapter URL hash should be included in downloaded chapter filenames.
     *
     * @return `true` if the chapter URL hash is included in downloaded chapter filenames, `false` otherwise.
     */
    fun includeChapterUrlHash() = preferenceStore.getBoolean("download_include_chapter_url_hash", true)

    /**
     * Gets the configured interval, in minutes, for the download worker schedule.
     *
     * @return The interval in minutes; `0` disables the worker. Allowed periodic values are `15`, `30`, `60`, `180`, and `360`. Default is `15`.
     */
    fun downloadWorkerInterval() = preferenceStore.getInt("download_worker_interval", 15)

    /**
     * Indicates whether automatic downloading of chapters from the user's reading history is enabled.
     *
     * @return `true` if enabled, `false` otherwise.
     */
    fun autoDownloadFromReadingHistory() = preferenceStore.getBoolean("auto_download_from_reading_history", false)

    /**
     * Number of days to consider from reading history when auto-downloading chapters.
     *
     * @return The number of days in the reading-history window used for auto-downloads (default 7).
     */
    fun autoDownloadReadingHistoryDays() = preferenceStore.getInt("auto_download_reading_history_days", 7)

    /**
     * Maximum number of retry attempts for automatic downloads.
     *
     * @return The configured maximum retry count for auto-downloads; defaults to 5.
     */
    fun autoDownloadMaxRetries() = preferenceStore.getInt("auto_download_max_retries", 5)

    /**
     * Determines whether orphaned download folders are removed automatically when the app starts.
     *
     * @return `true` if orphaned download folders should be cleaned up on startup, `false` otherwise.
     */
    fun cleanupOrphanedFoldersOnStartup() = preferenceStore.getBoolean("cleanup_orphaned_folders_on_startup", true)

    companion object {
        private const val REMOVE_EXCLUDE_CATEGORIES_PREF_KEY = "remove_exclude_categories"
        private const val DOWNLOAD_NEW_CATEGORIES_PREF_KEY = "download_new_categories"
        private const val DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY = "download_new_categories_exclude"
        val categoryPreferenceKeys = setOf(
            REMOVE_EXCLUDE_CATEGORIES_PREF_KEY,
            DOWNLOAD_NEW_CATEGORIES_PREF_KEY,
            DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY,
        )
    }
}
