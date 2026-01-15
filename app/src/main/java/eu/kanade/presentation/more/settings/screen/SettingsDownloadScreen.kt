package eu.kanade.presentation.more.settings.screen

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.fastMap
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.widget.TriStateListDialog
import eu.kanade.tachiyomi.data.download.AutoDownloadPollingWorker
import eu.kanade.tachiyomi.data.download.DownloadJob
import eu.kanade.tachiyomi.data.download.TempFolderCleanupWorker
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.i18n.pluralStringResource as pluralStringResourceContext
import tachiyomi.core.common.i18n.stringResource as stringResourceContext

object SettingsDownloadScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_downloads

    /**
     * Builds the top-level list of download-related preference items displayed on the settings screen.
     *
     * Observes category and download preference state so the returned preferences reflect current values and react to changes.
     *
     * @return A list of `Preference` items representing individual download options (switches, sliders, lists, and grouped sections).
     */
    @Composable
    override fun getPreferences(): List<Preference> {
        val getCategories = remember { Injekt.get<GetCategories>() }
        val allCategories by getCategories.subscribe().collectAsState(initial = emptyList())

        val downloadPreferences = remember { Injekt.get<DownloadPreferences>() }
        val parallelSourceLimit by downloadPreferences.parallelSourceLimit().collectAsState()
        val parallelPageLimit by downloadPreferences.parallelPageLimit().collectAsState()
        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = downloadPreferences.downloadOnlyOverWifi(),
                title = stringResource(MR.strings.connected_to_wifi),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = downloadPreferences.saveChaptersAsCBZ(),
                title = stringResource(MR.strings.save_chapter_as_cbz),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = downloadPreferences.splitTallImages(),
                title = stringResource(MR.strings.split_tall_images),
                subtitle = stringResource(MR.strings.split_tall_images_summary),
            ),
            Preference.PreferenceItem.SliderPreference(
                value = parallelSourceLimit,
                valueRange = 1..10,
                title = stringResource(MR.strings.pref_download_concurrent_sources),
                onValueChanged = { downloadPreferences.parallelSourceLimit().set(it) },
            ),
            Preference.PreferenceItem.SliderPreference(
                value = parallelPageLimit,
                valueRange = 1..15,
                title = stringResource(MR.strings.pref_download_concurrent_pages),
                subtitle = stringResource(MR.strings.pref_download_concurrent_pages_summary),
                onValueChanged = { downloadPreferences.parallelPageLimit().set(it) },
            ),
            getDownloadQueueGroup(downloadPreferences = downloadPreferences),
            getDeleteChaptersGroup(
                downloadPreferences = downloadPreferences,
                categories = allCategories,
            ),
            getAutoDownloadGroup(
                downloadPreferences = downloadPreferences,
                allCategories = allCategories,
            ),
            getAutoDownloadAdvancedGroup(downloadPreferences = downloadPreferences),
            getDownloadAheadGroup(downloadPreferences = downloadPreferences),
            getStorageCleanupGroup(downloadPreferences = downloadPreferences),
        )
    }

    @Composable
    private fun getDeleteChaptersGroup(
        downloadPreferences: DownloadPreferences,
        categories: List<Category>,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_delete_chapters),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.removeAfterMarkedAsRead(),
                    title = stringResource(MR.strings.pref_remove_after_marked_as_read),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = downloadPreferences.removeAfterReadSlots(),
                    entries = persistentMapOf(
                        -1 to stringResource(MR.strings.disabled),
                        0 to stringResource(MR.strings.last_read_chapter),
                        1 to stringResource(MR.strings.second_to_last),
                        2 to stringResource(MR.strings.third_to_last),
                        3 to stringResource(MR.strings.fourth_to_last),
                        4 to stringResource(MR.strings.fifth_to_last),
                    ),
                    title = stringResource(MR.strings.pref_remove_after_read),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.removeBookmarkedChapters(),
                    title = stringResource(MR.strings.pref_remove_bookmarked_chapters),
                ),
                getExcludedCategoriesPreference(
                    downloadPreferences = downloadPreferences,
                    categories = { categories },
                ),
            ),
        )
    }

    @Composable
    private fun getExcludedCategoriesPreference(
        downloadPreferences: DownloadPreferences,
        categories: () -> List<Category>,
    ): Preference.PreferenceItem.MultiSelectListPreference {
        return Preference.PreferenceItem.MultiSelectListPreference(
            preference = downloadPreferences.removeExcludeCategories(),
            entries = categories()
                .associate { it.id.toString() to it.visualName }
                .toImmutableMap(),
            title = stringResource(MR.strings.pref_remove_exclude_categories),
        )
    }

    @Composable
    private fun getAutoDownloadGroup(
        downloadPreferences: DownloadPreferences,
        allCategories: List<Category>,
    ): Preference.PreferenceGroup {
        val downloadNewChaptersPref = downloadPreferences.downloadNewChapters()
        val downloadNewUnreadChaptersOnlyPref = downloadPreferences.downloadNewUnreadChaptersOnly()
        val downloadNewChapterCategoriesPref = downloadPreferences.downloadNewChapterCategories()
        val downloadNewChapterCategoriesExcludePref = downloadPreferences.downloadNewChapterCategoriesExclude()

        val downloadNewChapters by downloadNewChaptersPref.collectAsState()

        val included by downloadNewChapterCategoriesPref.collectAsState()
        val excluded by downloadNewChapterCategoriesExcludePref.collectAsState()
        var showDialog by rememberSaveable { mutableStateOf(false) }
        if (showDialog) {
            TriStateListDialog(
                title = stringResource(MR.strings.categories),
                message = stringResource(MR.strings.pref_download_new_categories_details),
                items = allCategories,
                initialChecked = included.mapNotNull { id -> allCategories.find { it.id.toString() == id } },
                initialInversed = excluded.mapNotNull { id -> allCategories.find { it.id.toString() == id } },
                itemLabel = { it.visualName },
                onDismissRequest = { showDialog = false },
                onValueChanged = { newIncluded, newExcluded ->
                    downloadNewChapterCategoriesPref.set(newIncluded.fastMap { it.id.toString() }.toSet())
                    downloadNewChapterCategoriesExcludePref.set(newExcluded.fastMap { it.id.toString() }.toSet())
                    showDialog = false
                },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_auto_download),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadNewChaptersPref,
                    title = stringResource(MR.strings.pref_download_new),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadNewUnreadChaptersOnlyPref,
                    title = stringResource(MR.strings.pref_download_new_unread_chapters_only),
                    enabled = downloadNewChapters,
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.categories),
                    subtitle = getCategoriesLabel(
                        allCategories = allCategories,
                        included = included,
                        excluded = excluded,
                    ),
                    enabled = downloadNewChapters,
                    onClick = { showDialog = true },
                ),
            ),
        )
    }

    /**
     * Creates the "Download ahead" preference group for configuring automatic downloads while reading.
     *
     * @param downloadPreferences Preferences source used to back the group's settings.
     * @return A Preference.PreferenceGroup containing a list preference for selecting how many upcoming chapters to download while reading and an informational preference explaining the feature.
     */
    @Composable
    private fun getDownloadAheadGroup(
        downloadPreferences: DownloadPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.download_ahead),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = downloadPreferences.autoDownloadWhileReading(),
                    entries = listOf(0, 2, 3, 5, 10)
                        .associateWith {
                            if (it == 0) {
                                stringResource(MR.strings.disabled)
                            } else {
                                pluralStringResource(MR.plurals.next_unread_chapters, count = it, it)
                            }
                        }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.auto_download_while_reading),
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.download_ahead_info)),
            ),
        )
    }

    /**
     * Builds the "Download Queue" preference group for download-related settings.
     *
     * Provides a list preference to configure the periodic worker interval (scheduling periodic work when changed)
     * and a list preference to configure the maximum automatic download retries.
     *
     * @param downloadPreferences Preferences store for download-related settings used to back the preferences.
     * @return A Preference.PreferenceGroup containing the download worker interval and max-retries list preferences.
     */
    @Composable
    private fun getDownloadQueueGroup(
        downloadPreferences: DownloadPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_download_queue),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = downloadPreferences.downloadWorkerInterval(),
                    entries = persistentMapOf(
                        0 to stringResource(MR.strings.disabled),
                        15 to stringResource(MR.strings.update_15min),
                        30 to stringResource(MR.strings.update_30min),
                        60 to stringResource(MR.strings.update_60min),
                        180 to stringResource(MR.strings.update_3hour),
                        360 to stringResource(MR.strings.update_6hour),
                    ),
                    title = stringResource(MR.strings.pref_download_worker_interval),
                    subtitle = stringResource(MR.strings.pref_download_worker_interval_summary),
                    onValueChanged = { newValue ->
                        DownloadJob.setupPeriodicWork(context, newValue)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = downloadPreferences.autoDownloadMaxRetries(),
                    entries = persistentMapOf(
                        3 to "3",
                        5 to "5",
                        10 to "10",
                        999 to stringResource(MR.strings.unlimited),
                    ),
                    title = stringResource(MR.strings.pref_max_download_retries),
                ),
            ),
        )
    }

    /**
     * Builds the "Auto-download (advanced)" preference group for reading-history based auto-download settings.
     *
     * Provides a switch to enable or disable auto-downloading from reading history and a slider to select
     * how many days of reading history to consider; the slider is enabled only when the switch is on.
     *
     * @return A Preference.PreferenceGroup containing the switch for enabling periodic auto-downloads and
     * a slider for the lookback days used when sourcing chapters from reading history.
     */
    @Composable
    private fun getAutoDownloadAdvancedGroup(
        downloadPreferences: DownloadPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val autoDownloadFromHistory by downloadPreferences.autoDownloadFromReadingHistory().collectAsState()
        val lookbackDays by downloadPreferences.autoDownloadReadingHistoryDays().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_auto_download_advanced),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.autoDownloadFromReadingHistory(),
                    title = stringResource(MR.strings.pref_auto_download_reading_history),
                    subtitle = stringResource(MR.strings.pref_auto_download_reading_history_summary),
                    onValueChanged = { newValue ->
                        AutoDownloadPollingWorker.setupPeriodicWork(context, newValue)
                        true
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = lookbackDays,
                    valueRange = 3..30,
                    title = stringResource(MR.strings.pref_reading_history_lookback),
                    subtitle = stringResource(MR.strings.pref_reading_history_lookback_summary),
                    valueString = pluralStringResource(MR.plurals.day, lookbackDays, lookbackDays),
                    enabled = autoDownloadFromHistory,
                    onValueChanged = { downloadPreferences.autoDownloadReadingHistoryDays().set(it) },
                ),
            ),
        )
    }

    /**
     * Builds the "Storage cleanup" preference group for downloads.
     *
     * The group contains a switch to enable or disable cleanup of orphaned temporary folders on startup
     * and a "Cleanup now" action that triggers immediate cleanup and reports the result to the user.
     *
     * @param downloadPreferences Preferences used to back the cleanup switch and related settings.
     * @return A Preference.PreferenceGroup with storage cleanup preferences and actions.
     */
    @Composable
    private fun getStorageCleanupGroup(
        downloadPreferences: DownloadPreferences,
    ): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        var isCleaning by rememberSaveable { mutableStateOf(false) }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_storage_cleanup),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.cleanupOrphanedFoldersOnStartup(),
                    title = stringResource(MR.strings.pref_cleanup_on_startup),
                    subtitle = stringResource(MR.strings.pref_cleanup_on_startup_summary),
                    onValueChanged = { newValue ->
                        TempFolderCleanupWorker.setupPeriodicWork(context, newValue)
                        true
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_cleanup_now),
                    subtitle = stringResource(MR.strings.pref_cleanup_now_summary),
                    enabled = !isCleaning,
                    onClick = {
                        context.toast(MR.strings.cleanup_temp_folders_started)
                        scope.launch {
                            if (isCleaning) return@launch
                            isCleaning = true
                            val cleaned = withIOContext {
                                TempFolderCleanupWorker.cleanupOrphanedTempFolders(maxAgeMillis = 0)
                            }
                            val message = if (cleaned == 0) {
                                context.stringResourceContext(MR.strings.cleanup_temp_folders_none)
                            } else {
                                context.pluralStringResourceContext(
                                    MR.plurals.cleanup_temp_folders_done,
                                    cleaned,
                                    cleaned,
                                )
                            }
                            context.toast(message, Toast.LENGTH_LONG)
                            isCleaning = false
                        }
                    },
                ),
            ),
        )
    }
}
